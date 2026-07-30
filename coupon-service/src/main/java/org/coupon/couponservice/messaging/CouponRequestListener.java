package org.coupon.couponservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.event.SagaTopics;
import org.coupon.couponservice.service.CouponSagaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code coupon-request}를 소비한다. 정상 적용과 보상 복구가 같은 토픽으로 오고
 * {@code couponOrderStatus}로 갈린다 — 토픽이 아니라 DTO의 상태로 분기하는 것이 이 설계의 핵심이다.
 *
 * <p><b>{@code groupId}를 반드시 명시한다.</b> {@code config/coupon-service.yml}의 전역 group-id는
 * 선착순 발급 컨슈머({@code coupon-issue})의 것이다. 그대로 물려받으면 사가와 발급이 오프셋과
 * 리밸런싱을 공유해 서로를 끌어내린다. 과거 Spring Cloud Bus가 같은 설정 상속 문제로
 * {@code No type information in headers}를 2만 건 넘게 낸 전례가 있다.
 *
 * <p><b>batch listener이며 오프셋 커밋은 컨테이너에 맡긴다.</b> 발급 컨슈머가 쓰는
 * 단건 + 수동 ack와 다른데, 성격이 다르기 때문이다 — 발급은 저장 실패를 재시도로 살려야 하고,
 * 사가의 중복 응답은 "이미 처리됨"이라 재시도할 이유가 없다.
 *
 * <p>판정 기준은 하나다 — <b>"이 예외가 '이미 처리됨'을 뜻하는가, '아직 처리 못 함'을 뜻하는가."</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRequestListener {

    private final CouponSagaService couponSagaService;

    @KafkaListener(
            id = "coupon-saga",
            groupId = "coupon-saga",
            topics = SagaTopics.COUPON_REQUEST,
            containerFactory = "sagaListenerContainerFactory")
    public void receive(List<CouponRequest> requests) {
        for (int index = 0; index < requests.size(); index++) {
            CouponRequest request = requests.get(index);

            try {
                couponSagaService.handle(request);
            } catch (DataIntegrityViolationException e) {
                // UNIQUE 위반 = 다른 스레드가 같은 요청을 먼저 처리하고 커밋했다.
                // 그 스레드의 응답이 나가므로 여기서는 물러난다.
                log.info("동시 중복 요청, 다른 스레드가 이미 처리했다: sagaId={}, requestStatus={}",
                        request.sagaId(), request.couponOrderStatus());
            } catch (OptimisticLockingFailureException e) {
                // 같은 Outbox row를 두 스레드가 동시에 건드렸다. 재시도해도 결과가 같다.
                log.info("동시 처리 감지, 이 스레드는 물러난다: sagaId={}", request.sagaId());
            } catch (Exception e) {
                // DB 연결 실패, 역직렬화 실패, 예상 못 한 오류는 삼키면 요청이 영구 유실된다.
                // index를 반드시 실어야 배치 전체가 아니라 실패한 레코드 하나만 재시도된다.
                throw new BatchListenerFailedException("쿠폰 요청 처리 실패", e, index);
            }
        }
    }
}
