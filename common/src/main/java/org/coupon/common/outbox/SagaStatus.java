package org.coupon.common.outbox;

public enum SagaStatus {

    STARTED,

    PROCESSING,

    SUCCEEDED,

    FAILED,

    COMPENSATING,

    COMPENSATED
}
