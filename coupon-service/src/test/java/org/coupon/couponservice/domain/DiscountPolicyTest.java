package org.coupon.couponservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountPolicyTest {

    @Nested
    @DisplayName("RateDiscountPolicy(정률)")
    class Rate {

        @Test
        @DisplayName("원가의 rate%를 할인하고 소수점은 버린다(floor)")
        void discount() {
            assertThat(new RateDiscountPolicy(10L).discount(10000L)).isEqualTo(1000L);
            assertThat(new RateDiscountPolicy(100L).discount(10000L)).isEqualTo(10000L);
            assertThat(new RateDiscountPolicy(10L).discount(9999L)).isEqualTo(999L); // 999.9 → 999
        }
    }

    @Nested
    @DisplayName("FixDiscountPolicy(정액)")
    class Fix {

        @Test
        @DisplayName("원가와 무관하게 정액을 반환한다(원가 초과 시 0 하한은 Coupon이 흡수)")
        void discount() {
            assertThat(new FixDiscountPolicy(3000L).discount(10000L)).isEqualTo(3000L);
            assertThat(new FixDiscountPolicy(15000L).discount(10000L)).isEqualTo(15000L);
        }
    }

    @Nested
    @DisplayName("DiscountPolicyFactory")
    class Factory {

        @Test
        @DisplayName("PERCENT 쿠폰이면 RateDiscountPolicy를 만든다")
        void createRate() {
            Coupon coupon = Coupon.create("정률 쿠폰", 100L, DiscountType.PERCENT, 10L);

            DiscountPolicy policy = DiscountPolicyFactory.create(coupon);

            assertThat(policy).isInstanceOf(RateDiscountPolicy.class);
            assertThat(policy.discount(10000L)).isEqualTo(1000L);
        }

        @Test
        @DisplayName("FIXED_AMOUNT 쿠폰이면 FixDiscountPolicy를 만든다")
        void createFix() {
            Coupon coupon = Coupon.create("정액 쿠폰", 100L, DiscountType.FIXED_AMOUNT, 3000L);

            DiscountPolicy policy = DiscountPolicyFactory.create(coupon);

            assertThat(policy).isInstanceOf(FixDiscountPolicy.class);
            assertThat(policy.discount(10000L)).isEqualTo(3000L);
        }
    }

    @Nested
    @DisplayName("Coupon.finalPrice(전략 위임 + 0 하한)")
    class FinalPrice {

        @Test
        @DisplayName("PERCENT 쿠폰의 최종가")
        void percent() {
            assertThat(Coupon.create("정률 쿠폰", 100L, DiscountType.PERCENT, 10L).finalPrice(10000L))
                    .isEqualTo(9000L);
            assertThat(Coupon.create("전액 쿠폰", 100L, DiscountType.PERCENT, 100L).finalPrice(10000L))
                    .isEqualTo(0L);
        }

        @Test
        @DisplayName("FIXED_AMOUNT 쿠폰의 최종가는 0 미만으로 내려가지 않는다")
        void fixedAmount() {
            assertThat(Coupon.create("정액 쿠폰", 100L, DiscountType.FIXED_AMOUNT, 3000L).finalPrice(10000L))
                    .isEqualTo(7000L);
            assertThat(Coupon.create("큰 정액 쿠폰", 100L, DiscountType.FIXED_AMOUNT, 15000L).finalPrice(10000L))
                    .isEqualTo(0L);
        }
    }
}
