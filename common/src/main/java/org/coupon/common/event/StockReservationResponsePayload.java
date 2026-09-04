package org.coupon.common.event;

public record StockReservationResponsePayload(
        Long orderId,
        String productName,
        Long unitPrice,
        StockResult result,
        String failureCode
) {
}
