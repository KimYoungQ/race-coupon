package org.coupon.orderservice.dto;

import org.coupon.orderservice.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세. 사가가 진행 중이면 금액이 0이고, 실패했다면 {@code failureCode}가 채워진다.
 */
public record OrderResponse(
        Long orderId,
        OrderStatus status,
        Long couponId,
        Long totalAmount,
        Long discountAmount,
        Long finalAmount,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemResponse> items
) {
}
