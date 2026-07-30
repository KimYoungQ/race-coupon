package org.coupon.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.SagaTopics;
import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.coupon.orderservice.saga.OrderCouponSaga;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code coupon-response}를 소비한다. 분기 방식과 예외 판정 기준은
 * {@link ProductResponseListener}와 같다 — 근거는 그쪽 javadoc을 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponResponseListener {

    private final OrderCouponSaga orderCouponSaga;

    @KafkaListener(
            id = "order-coupon-saga",
            groupId = "order-saga",
            topics = SagaTopics.COUPON_RESPONSE,
            containerFactory = "sagaListenerContainerFactory")
    public void receive(List<CouponResponse> responses) {
        for (int index = 0; index < responses.size(); index++) {
            CouponResponse response = responses.get(index);

            try {
                dispatch(response);
            } catch (OptimisticLockingFailureException e) {
                log.info("동시 중복 응답 무시: sagaId={}", response.sagaId());
            } catch (InvalidOrderStateException e) {
                log.info("지연/무효 응답 무시: sagaId={}, couponStatus={}",
                        response.sagaId(), response.couponStatus());
            } catch (Exception e) {
                throw new BatchListenerFailedException("쿠폰 응답 처리 실패", e, index);
            }
        }
    }

    private void dispatch(CouponResponse response) {
        switch (response.couponStatus()) {
            case APPLIED -> orderCouponSaga.couponApplied(response);
            case RESTORED -> orderCouponSaga.couponRestored(response);
            case FAILED -> orderCouponSaga.couponFailed(response);
        }
    }
}
