package org.coupon.couponservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountDecoratorTest {

    @Nested
    @DisplayName("MaxDiscountDecorator(최대 할인 한도)")
    class MaxDiscount {

        @Test
        @DisplayName("할인액이 한도를 넘으면 한도로 상한한다")
        void caps() {
            DiscountPolicy policy = new MaxDiscountDecorator(new RateDiscountPolicy(20L), 30000L);

            // 200,000 * 20% = 40,000 → 한도 30,000
            assertThat(policy.discount(200000L)).isEqualTo(30000L);
        }

        @Test
        @DisplayName("할인액이 한도 미만이면 그대로 둔다")
        void underCap() {
            DiscountPolicy policy = new MaxDiscountDecorator(new RateDiscountPolicy(10L), 30000L);

            // 100,000 * 10% = 10,000 → 한도 미만
            assertThat(policy.discount(100000L)).isEqualTo(10000L);
        }
    }

    @Nested
    @DisplayName("MinOrderAmountDecorator(최소 주문 금액)")
    class MinOrderAmount {

        @Test
        @DisplayName("주문금액이 기준 이상이면 할인을 적용한다")
        void applies() {
            DiscountPolicy policy = new MinOrderAmountDecorator(new RateDiscountPolicy(10L), 50000L);

            assertThat(policy.discount(60000L)).isEqualTo(6000L);
        }

        @Test
        @DisplayName("주문금액이 기준 미만이면 할인 0으로 단락한다")
        void gatesOut() {
            DiscountPolicy policy = new MinOrderAmountDecorator(new RateDiscountPolicy(10L), 50000L);

            assertThat(policy.discount(40000L)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("조합: Min(Max(Rate))")
    class Composed {

        private DiscountPolicy policy() {
            // 정률 20% + 최대 30,000 한도 + 최소 주문 50,000
            return new MinOrderAmountDecorator(
                    new MaxDiscountDecorator(new RateDiscountPolicy(20L), 30000L),
                    50000L);
        }

        @Test
        @DisplayName("게이트 통과 시 한도 캡이 적용된다")
        void passGateAndCap() {
            // 200,000 ≥ 50,000 통과 → 40,000 → 한도 30,000
            assertThat(policy().discount(200000L)).isEqualTo(30000L);
        }

        @Test
        @DisplayName("게이트 미달이면 캡·계산과 무관하게 0이다")
        void gateBlocksAll() {
            // 40,000 < 50,000 → 0
            assertThat(policy().discount(40000L)).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("전략(팩토리) + 데코레이터 조합 — 실제 사용 흐름")
    class WithFactoryBase {

        @Test
        @DisplayName("PERCENT 쿠폰: 팩토리 base(정률) + 최대 한도")
        void percentBaseWithCap() {
            Coupon coupon = Coupon.create("정률 쿠폰", 100L, DiscountType.PERCENT, 20L);

            DiscountPolicy base = DiscountPolicyFactory.create(coupon); // RateDiscountPolicy(20)
            DiscountPolicy policy = new MaxDiscountDecorator(base, 30000L);

            // 200,000 * 20% = 40,000 → 한도 30,000
            assertThat(policy.discount(200000L)).isEqualTo(30000L);
        }

        @Test
        @DisplayName("FIXED_AMOUNT 쿠폰: 팩토리 base(정액) + 최대 한도")
        void fixedBaseWithCap() {
            Coupon coupon = Coupon.create("정액 쿠폰", 100L, DiscountType.FIXED_AMOUNT, 50000L);

            DiscountPolicy base = DiscountPolicyFactory.create(coupon); // FixDiscountPolicy(50000)
            DiscountPolicy policy = new MaxDiscountDecorator(base, 30000L);

            // 정액 50,000 → 한도 30,000
            assertThat(policy.discount(200000L)).isEqualTo(30000L);
        }

        @Test
        @DisplayName("전략 + 최대 한도 + 최소 주문을 모두 겹쳐 적용한다")
        void fullStack() {
            Coupon coupon = Coupon.create("정률 쿠폰", 100L, DiscountType.PERCENT, 20L);

            DiscountPolicy policy = new MinOrderAmountDecorator(
                    new MaxDiscountDecorator(DiscountPolicyFactory.create(coupon), 30000L),
                    50000L);

            assertThat(policy.discount(200000L)).isEqualTo(30000L); // 게이트 통과 + 캡
            assertThat(policy.discount(40000L)).isEqualTo(0L);      // 게이트 미달
        }
    }

    @Nested
    @DisplayName("쿠폰 조건으로 팩토리가 자동 조립 → finalPrice에 반영")
    class CouponConditions {

        @Test
        @DisplayName("조건 없는 쿠폰은 기본 전략만 적용한다")
        void noConditions() {
            Coupon coupon = Coupon.create("정률 쿠폰", 100L, DiscountType.PERCENT, 10L);

            assertThat(coupon.finalPrice(10000L)).isEqualTo(9000L); // 1,000 할인
        }

        @Test
        @DisplayName("최대 한도·최소 주문 조건이 있으면 finalPrice가 자동으로 겹쳐 적용한다")
        void withConditions() {
            // 정률 20% + 최대 30,000 + 최소 주문 50,000
            Coupon coupon = Coupon.create("조건 쿠폰", 100L, DiscountType.PERCENT, 20L, 30000L, 50000L);

            // 200,000: 40,000 → 한도 30,000 → finalPrice 170,000
            assertThat(coupon.finalPrice(200000L)).isEqualTo(170000L);
            // 40,000 < 50,000: 게이트 미달 → 할인 0 → finalPrice 40,000
            assertThat(coupon.finalPrice(40000L)).isEqualTo(40000L);
        }

        @Test
        @DisplayName("최소 주문 조건만 있는 쿠폰")
        void onlyMinOrder() {
            Coupon coupon = Coupon.create("최소주문 쿠폰", 100L, DiscountType.PERCENT, 10L, null, 50000L);

            assertThat(coupon.finalPrice(60000L)).isEqualTo(54000L); // 6,000 할인
            assertThat(coupon.finalPrice(40000L)).isEqualTo(40000L); // 게이트 미달
        }
    }
}
