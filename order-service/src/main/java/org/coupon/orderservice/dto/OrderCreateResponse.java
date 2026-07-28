package org.coupon.orderservice.dto;

import org.coupon.orderservice.domain.OrderStatus;

/**
 * 주문 접수 응답. 이 시점에 확정된 것은 ORDERS row 한 줄뿐이라 금액을 담지 않는다.
 * 재고·쿠폰 처리 결과는 {@code GET /api/v1/orders/{orderId}}로 확인한다.
 */
public record OrderCreateResponse(
        Long orderId,
        OrderStatus status
) {
}
