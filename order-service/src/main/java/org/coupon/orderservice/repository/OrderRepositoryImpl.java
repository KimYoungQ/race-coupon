package org.coupon.orderservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.coupon.orderservice.domain.Order;

import java.util.List;
import java.util.Optional;

import static org.coupon.orderservice.domain.QOrder.order;
import static org.coupon.orderservice.domain.QOrderItem.orderItem;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public List<Order> findAllByUserIdWithItems(Long userId) {
        return queryFactory
                .selectFrom(order)
                .join(order.items, orderItem).fetchJoin()
                .where(order.userId.eq(userId))
                .orderBy(order.id.desc())
                .fetch();
    }

    public Optional<Order> findByIdAndUserIdWithItems(Long orderId, Long userId) {
        return Optional.ofNullable(queryFactory
                .selectFrom(order)
                .join(order.items, orderItem).fetchJoin()
                .where(order.id.eq(orderId), order.userId.eq(userId))
                .fetchOne());
    }
}
