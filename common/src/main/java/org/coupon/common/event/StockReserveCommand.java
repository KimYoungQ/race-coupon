package org.coupon.common.event;

public record StockReserveCommand(Long orderId, Long productId, Long userId, Long quantity) {

    public static final String TOPIC = "stock-reserve";
}
