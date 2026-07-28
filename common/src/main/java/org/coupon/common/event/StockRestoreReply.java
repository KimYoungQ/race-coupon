package org.coupon.common.event;

public record StockRestoreReply(Long orderId, Long productId, SagaResult result, String failureCode) {

    public static final String TOPIC = "stock-restore-reply";
}
