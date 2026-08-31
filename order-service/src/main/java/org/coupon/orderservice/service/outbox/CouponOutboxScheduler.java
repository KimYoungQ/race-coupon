package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.coupon.orderservice.kafka.SagaRequestPublisher;
import org.coupon.orderservice.saga.SagaChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponOutboxScheduler {

    private final CouponOutboxHelper couponOutboxHelper;
    private final SagaRequestPublisher sagaRequestPublisher;

    @Scheduled(
            fixedDelayString = "${order-service.outbox.coupon.fixed-delay:${order-service.outbox.fixed-delay:5000}}",
            initialDelayString = "${order-service.outbox.coupon.initial-delay:${order-service.outbox.initial-delay:10000}}")
    public void publishPending() {
        List<CouponOutbox> pending = couponOutboxHelper.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        log.info("쿠폰 요청 발행 {}건: {}", pending.size(),
                pending.stream()
                        .map(CouponOutbox::getId)
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        pending.forEach(outbox -> sagaRequestPublisher.publish(outbox.getId(), SagaChannel.COUPON));
    }
}
