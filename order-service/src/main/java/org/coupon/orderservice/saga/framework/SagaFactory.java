package org.coupon.orderservice.saga.framework;

@FunctionalInterface
public interface SagaFactory<S extends AbstractSaga> {

    S create(SagaState state, SagaContext context);
}
