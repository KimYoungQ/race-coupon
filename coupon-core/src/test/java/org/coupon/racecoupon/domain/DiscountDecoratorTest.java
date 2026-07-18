package org.coupon.racecoupon.domain;

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
}
