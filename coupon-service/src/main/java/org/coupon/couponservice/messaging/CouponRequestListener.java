package org.coupon.couponservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.event.SagaTopics;
import org.coupon.couponservice.saga.SagaTraceTag;
import org.coupon.couponservice.service.CouponSagaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRequestListener {

    private final CouponSagaService couponSagaService;
    private final SagaTraceTag sagaTraceTag;

    @KafkaListener(
            id = "coupon-saga",
            groupId = "coupon-saga",
            topics = SagaTopics.COUPON_REQUEST,
            containerFactory = "sagaListenerContainerFactory")
    public void receive(List<CouponRequest> requests) {
        for (int index = 0; index < requests.size(); index++) {
            CouponRequest request = requests.get(index);

            try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, request.sagaId())) {
                couponSagaService.handle(request);
            } catch (DataIntegrityViolationException e) {
                log.info("동시 중복 요청, 다른 스레드가 이미 처리했다: sagaId={}, requestStatus={}",
                        request.sagaId(), request.couponOrderStatus());
            } catch (OptimisticLockingFailureException e) {
                log.info("동시 처리 감지, 이 스레드는 물러난다: sagaId={}", request.sagaId());
            } catch (Exception e) {
                throw new BatchListenerFailedException("쿠폰 요청 처리 실패", e, index);
            }
        }
    }
}
