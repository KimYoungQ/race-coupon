package org.coupon.orderservice.saga;

import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderItem;

public record OrderSagaPayload(
        Long orderId,
        Long userId,
        Long productId,
        Integer quantity,
        Long couponId,
        Long orderAmount
) {

    public static OrderSagaPayload of(Order order) {
        OrderItem item = order.primaryItem();
        return new OrderSagaPayload(
                order.getId(), order.getUserId(), item.getProductId(), item.getQuantity(),
                order.getCouponId(), null);
    }

    public OrderSagaPayload withOrderAmount(long orderAmount) {
        return new OrderSagaPayload(orderId, userId, productId, quantity, couponId, orderAmount);
    }
}
