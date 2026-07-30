package org.coupon.orderservice.domain;

import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ORDERS row가 곧 사가 인스턴스다. 이 테스트는 그 상태 머신의 계약을 고정한다.
 *
 * <p>여기의 가드는 불변식 보호인 동시에 <b>중복 응답에 대한 최종 방어선</b>이다.
 * Kafka는 at-least-once라 같은 응답이 두 번 도착할 수 있고, 그때 금액이 두 번 반영되면 안 된다.
 */
class OrderSagaStateTest {

    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final long COUPON_ID = 9L;

    private Order order(Long couponId) {
        return Order.builder()
                .userId(USER_ID)
                .couponId(couponId)
                .productId(PRODUCT_ID)
                .quantity(2)
                .build();
    }

    private Order reserved(Long couponId) {
        Order order = order(couponId);
        order.reserveStock("무선 이어폰", 10_000L);
        return order;
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("CREATED로 시작하고 금액은 아직 0이다 — 단가는 재고 응답이 실어온다")
        void starts_created_with_zero_amounts() {
            Order order = order(COUPON_ID);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getTotalAmount()).isZero();
            assertThat(order.getDiscountAmount()).isZero();
            assertThat(order.getFinalAmount()).isZero();
            assertThat(order.primaryItem().getUnitPrice()).isNull();
        }

        @Test
        @DisplayName("sagaId가 발급되며 주문마다 다르다")
        void issues_unique_saga_id() {
            Order first = order(COUPON_ID);
            Order second = order(COUPON_ID);

            assertThat(first.getSagaId()).isNotNull();
            assertThat(second.getSagaId()).isNotNull();
            assertThat(first.getSagaId()).isNotEqualTo(second.getSagaId());
        }

        @Test
        @DisplayName("품목은 항상 정확히 하나다")
        void always_exactly_one_item() {
            assertThat(order(COUPON_ID).getItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("정상 경로")
    class HappyPath {

        @Test
        @DisplayName("재고 예약이 단가 스냅샷을 고정하고 총액을 확정한다")
        void reserve_stock_fixes_snapshot() {
            Order order = order(COUPON_ID);

            order.reserveStock("무선 이어폰", 10_000L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
            assertThat(order.primaryItem().getProductName()).isEqualTo("무선 이어폰");
            assertThat(order.primaryItem().getUnitPrice()).isEqualTo(10_000L);
            // 총액을 인자로 받지 않고 단가 * 수량으로 직접 계산한다
            assertThat(order.getTotalAmount()).isEqualTo(20_000L);
            assertThat(order.getFinalAmount()).isEqualTo(20_000L);
        }

        @Test
        @DisplayName("쿠폰 적용으로 완료되면 할인과 최종 금액이 반영된다")
        void complete_applies_discount() {
            Order order = reserved(COUPON_ID);

            order.complete(2_000L, 18_000L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.getDiscountAmount()).isEqualTo(2_000L);
            assertThat(order.getFinalAmount()).isEqualTo(18_000L);
        }

        @Test
        @DisplayName("쿠폰 없는 주문도 재고 예약을 먼저 거친 뒤 완료된다")
        void order_without_coupon_still_reserves_first() {
            Order order = order(null);
            assertThat(order.getCouponId()).isNull();

            order.reserveStock("무선 이어폰", 10_000L);
            order.complete(0L, order.getTotalAmount());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.getFinalAmount()).isEqualTo(20_000L);
        }
    }

    @Nested
    @DisplayName("보상 경로")
    class Compensation {

        @Test
        @DisplayName("쿠폰 실패는 COMPENSATING으로 보내고 실패 원인을 기록한다")
        void start_compensation_records_cause() {
            Order order = reserved(COUPON_ID);

            order.startCompensation("COUPON_ALREADY_USED");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
            assertThat(order.getFailureCode()).isEqualTo("COUPON_ALREADY_USED");
        }

        @Test
        @DisplayName("보상 완료는 원래 실패 원인을 그대로 유지한다")
        void complete_compensation_keeps_original_cause() {
            Order order = reserved(COUPON_ID);
            order.startCompensation("COUPON_ALREADY_USED");

            order.completeCompensation();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            // 사가를 무너뜨린 원인은 "재고를 되돌렸다"가 아니라 "쿠폰이 이미 사용됨"이다
            assertThat(order.getFailureCode()).isEqualTo("COUPON_ALREADY_USED");
        }

        /**
         * fail()은 COMPENSATING에서도 열려 있다. 도메인이 막지 않으므로 규율은 호출부가 지킨다 —
         * 이 테스트는 "막힌다"가 아니라 "안 막히고 원인이 덮인다"를 못 박아,
         * 보상 완료 경로에서 fail()을 쓰면 무엇을 잃는지 코드로 남긴다.
         */
        @Test
        @DisplayName("보상 완료에 fail()을 쓰면 원래 실패 원인이 덮인다 — completeCompensation()을 쓸 것")
        void fail_overwrites_original_cause_when_compensating() {
            Order order = reserved(COUPON_ID);
            order.startCompensation("COUPON_ALREADY_USED");

            order.fail("STOCK_RESTORED");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(order.getFailureCode())
                    .as("진짜 원인이 뒷정리 결과로 덮였다")
                    .isEqualTo("STOCK_RESTORED");
        }

        @Test
        @DisplayName("재고 예약 실패는 되돌릴 것이 없어 곧바로 FAILED가 된다")
        void stock_failure_fails_directly() {
            Order order = order(COUPON_ID);

            order.fail("PRODUCT_OUT_OF_STOCK");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(order.getFailureCode()).isEqualTo("PRODUCT_OUT_OF_STOCK");
        }
    }

    @Nested
    @DisplayName("상태 가드 — 중복 응답 방어선")
    class Guards {

        @Test
        @DisplayName("재고 예약 응답이 두 번 와도 총액이 두 번 반영되지 않는다")
        void duplicate_stock_response_is_rejected() {
            Order order = reserved(COUPON_ID);

            assertThatThrownBy(() -> order.reserveStock("무선 이어폰", 10_000L))
                    .isInstanceOf(InvalidOrderStateException.class);
            assertThat(order.getTotalAmount()).isEqualTo(20_000L);
        }

        @Test
        @DisplayName("완료된 주문에 쿠폰 응답이 한 번 더 와도 금액이 바뀌지 않는다")
        void duplicate_coupon_response_is_rejected() {
            Order order = reserved(COUPON_ID);
            order.complete(2_000L, 18_000L);

            assertThatThrownBy(() -> order.complete(2_000L, 18_000L))
                    .isInstanceOf(InvalidOrderStateException.class);
            assertThat(order.getDiscountAmount()).isEqualTo(2_000L);
            assertThat(order.getFinalAmount()).isEqualTo(18_000L);
        }

        @Test
        @DisplayName("재고 예약 없이 완료할 수 없다 — 금액 0짜리 COMPLETED 주문을 막는다")
        void cannot_complete_without_reserving() {
            Order order = order(COUPON_ID);

            assertThatThrownBy(() -> order.complete(0L, 0L))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("보상을 시작하지 않은 주문은 보상 완료로 갈 수 없다")
        void cannot_complete_compensation_without_starting() {
            Order order = reserved(COUPON_ID);

            assertThatThrownBy(order::completeCompensation)
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("종료된 사가에 늦게 도착한 응답은 모두 거부된다")
        void terminal_saga_rejects_late_responses() {
            Order order = reserved(COUPON_ID);
            order.complete(2_000L, 18_000L);

            assertThatThrownBy(() -> order.reserveStock("무선 이어폰", 10_000L))
                    .isInstanceOf(InvalidOrderStateException.class);
            assertThatThrownBy(() -> order.startCompensation("LATE"))
                    .isInstanceOf(InvalidOrderStateException.class);
            assertThatThrownBy(() -> order.fail("LATE"))
                    .isInstanceOf(InvalidOrderStateException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }
    }
}
