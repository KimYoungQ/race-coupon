package org.coupon.orderservice.dto;

public record OrderItemResponse(
        Long productId,
        String productName,
        Long unitPrice,
        Integer quantity
) {
}
