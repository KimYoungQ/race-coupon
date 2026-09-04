package org.coupon.orderservice.saga.framework;

import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.springframework.context.ApplicationEventPublisher;

public record SagaContext(
        ApplicationEventPublisher eventPublisher,
        SagaPayloadCodec codec
) {
}
