package org.coupon.orderservice.saga.framework;

public record SagaStepMessage(
        String aggregateType,
        String type,
        Object payload
) {
}
