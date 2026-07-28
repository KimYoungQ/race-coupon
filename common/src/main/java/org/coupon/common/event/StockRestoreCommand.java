package org.coupon.common.event;

public record StockRestoreCommand(Long orderId, Long productId, Long quantity) {

    public static final String TOPIC = "stock-restore";
}
