package org.coupon.orderservice.domain;

import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class OrderTest {

    @Test
    @DisplayName("재고가 예약되면 주문은 STOCK_RESERVED 상태가 되고 총액이 확정된다")
    void reserveStock() {
        // given
        Order order = newOrder();

        // when
        order.reserveStock("무선 이어폰", 10_000L);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
        assertThat(order.getTotalAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("재고 예약 없이 주문을 완료할 수 없다")
    void cannotCompleteBeforeReserve() {
        // given
        Order order = newOrder();

        // when
        Throwable thrown = catchThrowable(() -> order.complete(0L, 0L));

        // then
        assertThat(thrown).isInstanceOf(InvalidOrderStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    private Order newOrder() {
        return Order.builder()
                .userId(42L)
                .couponId(9L)
                .productId(7L)
                .quantity(2)
                .build();
    }
}
