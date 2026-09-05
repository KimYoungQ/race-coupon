package org.coupon.couponservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("finalPrice")
    class FinalPrice {

        @Test
        @DisplayName("원가를 넘는 정액 쿠폰이어도 최종가가 0 밑으로 내려가지 않는다")
        void never_goes_below_zero() {
            Coupon coupon = fixedAmount(5_000L);
            long price = 1_000L;

            assertThat(DiscountPolicyFactory.create(coupon).discount(price)).isEqualTo(5_000L);
            assertThat(coupon.finalPrice(price)).isZero();
        }

        @Test
        @DisplayName("최대 할인 한도가 있으면 한도까지만 깎인다")
        void respects_max_discount() {
            Coupon coupon = percent(20L, 30_000L, null);
            long price = 200_000L;

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

            assertThat(coupon.finalPrice(price)).isEqualTo(price);
            assertThat(coupon.satisfiesMinOrderAmount(price)).isFalse();
        }
    }
}
