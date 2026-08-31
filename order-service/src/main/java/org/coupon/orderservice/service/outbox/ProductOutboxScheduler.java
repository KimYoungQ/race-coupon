package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
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
public class ProductOutboxScheduler {

    private final ProductOutboxHelper productOutboxHelper;
    private final SagaRequestPublisher sagaRequestPublisher;

    @Scheduled(
            fixedDelayString = "${order-service.outbox.product.fixed-delay:${order-service.outbox.fixed-delay:5000}}",
            initialDelayString = "${order-service.outbox.product.initial-delay:${order-service.outbox.initial-delay:10000}}")
    public void publishPending() {
        List<ProductOutbox> pending = productOutboxHelper.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        log.info("재고 요청 발행 {}건: {}", pending.size(),
                pending.stream()
                        .map(ProductOutbox::getId)
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        pending.forEach(outbox -> sagaRequestPublisher.publish(outbox.getId(), SagaChannel.PRODUCT));
    }
}
