package org.coupon.common.saga;

public enum SagaStepStatus {

    STARTED,

    SUCCEEDED,

    FAILED,

    COMPENSATING,

    COMPENSATED
}
