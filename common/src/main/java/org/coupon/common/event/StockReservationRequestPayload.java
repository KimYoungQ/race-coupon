package org.coupon.common.event;

public record StockReservationRequestPayload(
        Long orderId,
        Long productId,
        Integer quantity,
        RequestType type
) {
}
