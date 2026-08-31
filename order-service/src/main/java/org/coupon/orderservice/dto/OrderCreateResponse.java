package org.coupon.orderservice.dto;

import org.coupon.orderservice.domain.OrderStatus;

public record OrderCreateResponse(
        Long orderId,
        OrderStatus status
) {
}
