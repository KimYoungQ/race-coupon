package org.coupon.couponservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountPolicyTest {

    @Test
    @DisplayName("정률 쿠폰은 주문 금액의 비율만큼 할인된다")
    void percentDiscount() {
        // given
        Coupon coupon = coupon(DiscountType.PERCENT, 10L, null, null);

        // when
        long finalPrice = coupon.finalPrice(100_000L);

        // then
        assertThat(finalPrice).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("정액 쿠폰은 정해진 금액만큼 할인된다")
    void fixedAmountDiscount() {
        // given
        Coupon coupon = coupon(DiscountType.FIXED_AMOUNT, 3_000L, null, null);

        // when
        long finalPrice = coupon.finalPrice(10_000L);

        // then
        assertThat(finalPrice).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("최소 주문 금액에 미달하면 쿠폰을 적용할 수 없다")
    void minOrderAmountNotMet() {
        // given
        Coupon coupon = coupon(DiscountType.PERCENT, 10L, null, 50_000L);

        // when
        boolean satisfied = coupon.satisfiesMinOrderAmount(10_000L);

        // then
        assertThat(satisfied).isFalse();
    }

    @Test
    @DisplayName("최대 할인 금액이 있으면 그 금액까지만 할인된다")
    void maxDiscountAmountCapsDiscount() {
        // given
        Coupon coupon = coupon(DiscountType.PERCENT, 20L, 30_000L, null);

        // when
        long finalPrice = coupon.finalPrice(200_000L);

        // then
        assertThat(finalPrice).isEqualTo(170_000L);
    }

    private Coupon coupon(DiscountType type, long value, Long maxDiscountAmount, Long minOrderAmount) {
        return Coupon.builder()
                .title("테스트 쿠폰")
                .totalQuantity(100L)
                .discountType(type)
                .discountValue(value)
                .maxDiscountAmount(maxDiscountAmount)
                .minOrderAmount(minOrderAmount)
                .build();
    }
}
