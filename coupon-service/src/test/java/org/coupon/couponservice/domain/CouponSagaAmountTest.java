package org.coupon.couponservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사가 응답에 실리는 금액의 정합성.
 *
 * <p>{@code CouponResponse}는 {@code discountAmount}와 {@code finalAmount}를 함께 실어 보내고
 * order-service는 그 둘을 그대로 주문에 기록한다. 두 값이 서로 어긋나면 주문 이력이
 * {@code total - discount != final}인 상태로 남는데, 그 시점에는 아무도 예외를 던지지 않는다.
 */
class CouponSagaAmountTest {

    private Coupon fixedAmount(long value) {
        return Coupon.builder()
                .title("정액 " + value)
                .totalQuantity(100L)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(value)
                .build();
    }

    private Coupon percent(long rate, Long maxDiscount, Long minOrder) {
        return Coupon.builder()
                .title("정률 " + rate)
                .totalQuantity(100L)
                .discountType(DiscountType.PERCENT)
                .discountValue(rate)
                .maxDiscountAmount(maxDiscount)
                .minOrderAmount(minOrder)
                .build();
    }

    @Nested
    @DisplayName("discountFor")
    class DiscountFor {

        @Test
        @DisplayName("할인액과 최종가를 더하면 항상 원가가 된다")
        void is_consistent_with_final_price() {
            Coupon coupon = percent(10L, null, null);
            long price = 100_000L;

            assertThat(coupon.discountFor(price) + coupon.finalPrice(price)).isEqualTo(price);
        }

        @Test
        @DisplayName("원가를 넘는 정액 쿠폰이어도 할인액이 원가를 넘지 않는다")
        void never_exceeds_price() {
            // 정책이 계산하는 값은 5000이지만 실제로 깎이는 것은 1000원뿐이다.
            Coupon coupon = fixedAmount(5_000L);
            long price = 1_000L;

            assertThat(DiscountPolicyFactory.create(coupon).discount(price)).isEqualTo(5_000L);
            assertThat(coupon.discountFor(price)).isEqualTo(1_000L);
            assertThat(coupon.finalPrice(price)).isZero();
        }

        @Test
        @DisplayName("최대 할인 한도가 있으면 한도까지만 깎인다")
        void respects_max_discount() {
            Coupon coupon = percent(20L, 30_000L, null);
            long price = 200_000L;

            assertThat(coupon.discountFor(price)).isEqualTo(30_000L);
            assertThat(coupon.finalPrice(price)).isEqualTo(170_000L);
        }
    }

    @Nested
    @DisplayName("satisfiesMinOrderAmount")
    class MinOrderAmount {

        @Test
        @DisplayName("최소 주문 금액이 없으면 항상 충족이다")
        void null_means_no_condition() {
            assertThat(percent(10L, null, null).satisfiesMinOrderAmount(1L)).isTrue();
        }

        @Test
        @DisplayName("기준 이상이면 충족, 미만이면 미충족")
        void compares_against_threshold() {
            Coupon coupon = percent(10L, null, 50_000L);

            assertThat(coupon.satisfiesMinOrderAmount(50_000L)).isTrue();
            assertThat(coupon.satisfiesMinOrderAmount(49_999L)).isFalse();
        }

        @Test
        @DisplayName("미달이면 데코레이터는 조용히 할인 0을 주므로 사가는 이 판정을 먼저 해야 한다")
        void decorator_is_silent_so_saga_must_check_first() {
            Coupon coupon = percent(10L, null, 50_000L);
            long price = 10_000L;

            // 데코레이터만 믿으면 "할인 0으로 정상 완료"가 되어 쿠폰만 소진된다.
            assertThat(coupon.discountFor(price)).isZero();
            assertThat(coupon.finalPrice(price)).isEqualTo(price);
            // 그래서 사가는 이 판정으로 먼저 걸러 명시적으로 실패시킨다.
            assertThat(coupon.satisfiesMinOrderAmount(price)).isFalse();
        }
    }
}
