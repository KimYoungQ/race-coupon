package org.coupon.orderservice.messaging;

import org.coupon.common.saga.SagaStatus;


public record SagaResponseOutcome(
        boolean skipped,
        String stepResult,
        String failureCode,
        SagaStatus sagaStatus
) {

    public static SagaResponseOutcome skip() {
        return new SagaResponseOutcome(true, null, null, null);
    }

    public static SagaResponseOutcome handled(String stepResult, String failureCode, SagaStatus sagaStatus) {
        return new SagaResponseOutcome(false, stepResult, failureCode, sagaStatus);
    }

    public boolean isTerminal() {
        return sagaStatus == SagaStatus.COMPLETED || sagaStatus == SagaStatus.ABORTED;
    }
}
