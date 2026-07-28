package org.coupon.common.event;

public record StockReserveReply(Long orderId, Long productId, Long quantity, String productName,
                                Long unitPrice, SagaResult result, String failureCode) {

    public static final String TOPIC = "stock-reserve-reply";
}
