package org.coupon.common.event;

import org.coupon.common.saga.SagaStepStatus;

public enum CouponResult {

    APPLIED,

    REJECTED,

    CANCELLED;

    public SagaStepStatus toStepStatus() {
        return switch (this) {
            case APPLIED -> SagaStepStatus.SUCCEEDED;
            case REJECTED -> SagaStepStatus.FAILED;
            case CANCELLED -> SagaStepStatus.COMPENSATED;
        };
    }
}
