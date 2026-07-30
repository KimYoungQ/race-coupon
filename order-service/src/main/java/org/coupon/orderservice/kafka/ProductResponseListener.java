package org.coupon.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockResponse;
import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.coupon.orderservice.saga.OrderProductSaga;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code product-response}를 소비한다. 성공·복구·실패가 같은 토픽으로 오고
 * {@code stockStatus}로 갈린다 — <b>토픽이 아니라 DTO의 결과 상태로 분기</b>하는 것이 이 설계의 핵심이다.
 *
 * <p><b>batch listener이며 오프셋 커밋은 컨테이너에 맡긴다.</b> {@code finally}에서 ack하는 흔한 패턴은
 * DB 저장 실패까지 커밋해 메시지를 영구 유실시킨다. 이 메서드가 정상 반환할 때만 커밋된다.
 *
 * <p>판정 기준은 하나다 — <b>"이 예외가 '이미 처리됨'을 뜻하는가, '아직 처리 못 함'을 뜻하는가."</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductResponseListener {

    private final OrderProductSaga orderProductSaga;

    @KafkaListener(
            id = "order-product-saga",
            groupId = "order-saga",
            topics = SagaTopics.PRODUCT_RESPONSE,
            containerFactory = "sagaListenerContainerFactory")
    public void receive(List<StockResponse> responses) {
        for (int index = 0; index < responses.size(); index++) {
            StockResponse response = responses.get(index);

            try {
                dispatch(response);
            } catch (OptimisticLockingFailureException e) {
                // 다른 스레드가 같은 사가 전이를 이미 완료했다. 확인된 중복이다.
                log.info("동시 중복 응답 무시: sagaId={}", response.sagaId());
            } catch (InvalidOrderStateException e) {
                // 현재 상태에서 허용되지 않는 지연/중복 응답이다. 도메인 가드가 막았고
                // 재시도해도 결과가 같다. 던지면 무한 재시도 루프가 된다.
                log.info("지연/무효 응답 무시: sagaId={}, stockStatus={}",
                        response.sagaId(), response.stockStatus());
            } catch (Exception e) {
                // DB·Outbox 저장 실패, 역직렬화 실패, 계약 위반은 삼키면 사가가 영영 멈춘다.
                // index를 반드시 실어야 배치 전체가 아니라 실패한 레코드 하나만 재시도된다.
                throw new BatchListenerFailedException("재고 응답 처리 실패", e, index);
            }
        }
    }

    private void dispatch(StockResponse response) {
        switch (response.stockStatus()) {
            case RESERVED -> orderProductSaga.stockReserved(response);
            case RESTORED -> orderProductSaga.stockRestored(response);
            case FAILED -> orderProductSaga.stockFailed(response);
        }
    }
}
