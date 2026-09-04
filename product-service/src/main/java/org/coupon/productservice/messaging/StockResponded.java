package org.coupon.productservice.messaging;

import org.coupon.common.event.AggregateTypes;
import org.coupon.common.event.DomainEvent;
import org.coupon.common.event.StockReservationResponsePayload;

public record StockResponded(
        String sagaId,
        StockReservationResponsePayload payload
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return AggregateTypes.STOCK_RESERVATION;
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
