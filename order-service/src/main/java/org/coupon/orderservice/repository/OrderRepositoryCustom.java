package org.coupon.orderservice.repository;

import org.coupon.orderservice.domain.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryCustom {

    List<Order> findAllByUserIdWithItems(Long userId);

    Optional<Order> findByIdAndUserIdWithItems(Long orderId, Long userId);
}
