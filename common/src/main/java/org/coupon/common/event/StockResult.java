package org.coupon.common.event;

import org.coupon.common.saga.SagaStepStatus;

public enum StockResult {

    RESERVED,

    OUT_OF_STOCK,

    RELEASED;

    public SagaStepStatus toStepStatus() {
        return switch (this) {
            case RESERVED -> SagaStepStatus.SUCCEEDED;
            case OUT_OF_STOCK -> SagaStepStatus.FAILED;
            case RELEASED -> SagaStepStatus.COMPENSATED;
        };
    }
}
