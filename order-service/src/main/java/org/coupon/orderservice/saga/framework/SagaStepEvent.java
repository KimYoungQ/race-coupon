package org.coupon.orderservice.saga.framework;

import org.coupon.common.event.DomainEvent;

public record SagaStepEvent(
        String aggregateType,
        String aggregateId,
        String type,
        Object payload
) implements DomainEvent {
}
