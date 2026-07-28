package org.coupon.orderservice.repository;

import org.coupon.orderservice.domain.Order;

import java.util.List;
import java.util.Optional;

/**
 * {@code open-in-view: false} 아래에서는 트랜잭션이 끝난 뒤 LAZY 품목에 접근할 수 없다.
 * 조회 시점에 join fetch로 품목을 함께 잡아 서비스 안에서 DTO 변환을 끝낼 수 있게 한다.
 */
public interface OrderRepositoryCustom {

    List<Order> findAllByUserIdWithItems(Long userId);

    /**
     * 소유자까지 조건에 넣는다. 남의 주문이면 조회 자체가 비어 404가 되므로
     * "그 orderId는 존재한다"는 사실이 새지 않는다.
     */
    Optional<Order> findByIdAndUserIdWithItems(Long orderId, Long userId);
}
