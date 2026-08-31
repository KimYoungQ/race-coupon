package org.coupon.productservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.productservice.domain.outbox.OrderOutbox;
import org.coupon.productservice.kafka.StockResponsePublisher;
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
    private final StockResponsePublisher stockResponsePublisher;

    @Scheduled(fixedDelayString = "${product-service.outbox.fixed-delay:5000}",
            initialDelayString = "${product-service.outbox.initial-delay:10000}")
    public void publishPending() {
        List<OrderOutbox> pending = orderOutboxHelper.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        log.info("재고 응답 발행 {}건: {}", pending.size(),
                pending.stream()
                        .map(OrderOutbox::getId)
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        pending.forEach(outbox -> stockResponsePublisher.publish(outbox.getId()));
    }
}
