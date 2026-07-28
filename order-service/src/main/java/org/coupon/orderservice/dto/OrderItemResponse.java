package org.coupon.orderservice.dto;

/**
 * 주문 품목. {@code productName}·{@code unitPrice}는 주문 시점 스냅샷이며,
 * 재고 예약이 끝나기 전에는 null이다.
 */
public record OrderItemResponse(
        Long productId,
        String productName,
        Long unitPrice,
        Integer quantity
) {
}
