package org.coupon.couponservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.couponservice.domain.outbox.OrderOutbox;
import org.coupon.couponservice.messaging.CouponResponsePublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxScheduler {

    private final OrderOutboxHelper orderOutboxHelper;
    private final CouponResponsePublisher couponResponsePublisher;

    @Scheduled(fixedDelayString = "${coupon-service.outbox.fixed-delay:5000}",
            initialDelayString = "${coupon-service.outbox.initial-delay:10000}")
    public void publishPending() {
        List<OrderOutbox> pending = orderOutboxHelper.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        log.info("쿠폰 응답 발행 {}건: {}", pending.size(),
                pending.stream()
                        .map(OrderOutbox::getId)
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        pending.forEach(outbox -> couponResponsePublisher.publish(outbox.getId()));
    }
}
