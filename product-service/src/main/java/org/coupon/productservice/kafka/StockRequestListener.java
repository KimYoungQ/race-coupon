package org.coupon.productservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockRequest;
import org.coupon.productservice.saga.SagaTraceTag;
import org.coupon.productservice.service.StockSagaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code product-request}를 소비한다. 정상 예약과 보상 복구가 같은 토픽으로 오고
 * {@code stockOrderStatus}로 갈린다 — 토픽이 아니라 DTO의 상태로 분기하는 것이 이 설계의 핵심이다.
 *
 * <p><b>batch listener이며 오프셋 커밋은 컨테이너에 맡긴다.</b> 수동 ack를 쓰지 않는 이유는
 * {@code finally}에서 ack하는 흔한 패턴이 <b>DB 저장 실패까지 커밋</b>해 메시지를 영구 유실시키기 때문이다.
 * 이 메서드가 정상 반환하면 그때 컨테이너가 배치 오프셋을 커밋한다.
 *
 * <p>판정 기준은 하나다 — <b>"이 예외가 '이미 처리됨'을 뜻하는가, '아직 처리 못 함'을 뜻하는가."</b>
 * 앞이면 커밋하고 넘어가고, 뒤면 {@link BatchListenerFailedException}으로 올려 재시도/DLT에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRequestListener {

    private final StockSagaService stockSagaService;
    private final SagaTraceTag sagaTraceTag;

    @KafkaListener(
            id = "product-saga",
            groupId = "product-saga",
            topics = SagaTopics.PRODUCT_REQUEST,
            containerFactory = "sagaListenerContainerFactory")
    public void receive(List<StockRequest> requests) {
        for (int index = 0; index < requests.size(); index++) {
            StockRequest request = requests.get(index);

            // sagaId는 payload에서 얻는다 — 헤더가 필요 없으므로 리스너 시그니처를 바꾸지 않는다.
            // 배치 리스너에 @Headers 파라미터를 더하면 spring-kafka가 페이로드 인자를 판별하지 못해
            // 리스너가 호출되지 않고 레코드가 DLT로 빠진다.
            try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, request.sagaId())) {
                stockSagaService.handle(request);
            } catch (DataIntegrityViolationException e) {
                // UNIQUE 위반 = 다른 스레드가 같은 요청을 먼저 처리하고 커밋했다.
                // 그 스레드의 응답이 나가므로 여기서는 물러난다.
                log.info("동시 중복 요청, 다른 스레드가 이미 처리했다: sagaId={}, requestStatus={}",
                        request.sagaId(), request.stockOrderStatus());
            } catch (OptimisticLockingFailureException e) {
                // 같은 Outbox row를 두 스레드가 동시에 건드렸다. 재시도해도 결과가 같다.
                log.info("동시 처리 감지, 이 스레드는 물러난다: sagaId={}", request.sagaId());
            } catch (Exception e) {
                // DB 연결 실패, 역직렬화 실패, 예상 못 한 오류는 삼키면 요청이 영구 유실된다.
                // index를 반드시 실어야 배치 전체가 아니라 실패한 레코드 하나만 재시도된다.
                throw new BatchListenerFailedException("재고 요청 처리 실패", e, index);
            }
        }
    }
}
