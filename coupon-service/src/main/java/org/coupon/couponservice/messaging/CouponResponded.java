package org.coupon.couponservice.messaging;

import org.coupon.common.event.AggregateTypes;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.DomainEvent;

public record CouponResponded(
        String sagaId,
        CouponApplyResponsePayload payload
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.COUPON_APPLY;
    }

    @Override
    public String aggregateId() {
        return sagaId;
    }

    @Override
    public String type() {
        return payload.result().name();
    }
}
