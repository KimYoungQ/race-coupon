package org.coupon.orderservice.dto;

import org.coupon.orderservice.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

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
