package org.coupon.racecoupon.domain;

import org.coupon.racecoupon.exception.InvalidDiscountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountTypeTest {

    @Nested
    @DisplayName("PERCENT는 1~100만 허용한다")
    class Percent {

        @ParameterizedTest
        @ValueSource(longs = {1, 50, 100})
        @DisplayName("경계 포함 1~100은 통과한다")
        void valid(long value) {
            assertThatCode(() -> DiscountType.PERCENT.validate(value))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1, 101})
        @DisplayName("0 이하 또는 100 초과는 예외를 던진다")
        void invalid(long value) {
            assertThatThrownBy(() -> DiscountType.PERCENT.validate(value))
                    .isInstanceOf(InvalidDiscountException.class);
        }
    }

    @Nested
    @DisplayName("FIXED_AMOUNT는 양수만 허용한다")
    class FixedAmount {

        @ParameterizedTest
        @ValueSource(longs = {1, 1000, 100000})
        @DisplayName("양수는 통과한다")
        void valid(long value) {
            assertThatCode(() -> DiscountType.FIXED_AMOUNT.validate(value))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1})
        @DisplayName("0 이하는 예외를 던진다")
        void invalid(long value) {
            assertThatThrownBy(() -> DiscountType.FIXED_AMOUNT.validate(value))
                    .isInstanceOf(InvalidDiscountException.class);
        }
    }

    @Test
    @DisplayName("Coupon.create는 유형별 불변식을 위반하면 InvalidDiscountException을 던진다")
    void create_rejects_invalid_discount() {
        assertThatThrownBy(() -> Coupon.create("잘못된 쿠폰", 100L, DiscountType.PERCENT, 0L))
                .isInstanceOf(InvalidDiscountException.class);
    }

    @Test
    @DisplayName("Coupon.create는 유효한 할인 정보를 그대로 보관한다")
    void create_keeps_valid_discount() {
        Coupon coupon = Coupon.create("정액 쿠폰", 100L, DiscountType.FIXED_AMOUNT, 3000L);

        assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(coupon.getDiscountValue()).isEqualTo(3000L);
    }

    @Nested
    @DisplayName("할인액 계산(discount) 전략")
    class Discount {

        @Test
        @DisplayName("PERCENT는 정률로 계산하고 소수점은 버린다(floor)")
        void percent() {
            assertThat(DiscountType.PERCENT.discount(10000L, 10L)).isEqualTo(1000L);
            assertThat(DiscountType.PERCENT.discount(10000L, 100L)).isEqualTo(10000L);
            assertThat(DiscountType.PERCENT.discount(9999L, 10L)).isEqualTo(999L); // 999.9 → 999
        }

        @Test
        @DisplayName("FIXED_AMOUNT는 정액 차감하되 원가를 초과하지 않는다")
        void fixedAmount() {
            assertThat(DiscountType.FIXED_AMOUNT.discount(10000L, 3000L)).isEqualTo(3000L);
            assertThat(DiscountType.FIXED_AMOUNT.discount(10000L, 15000L)).isEqualTo(10000L); // 상한
        }
    }

    @Nested
    @DisplayName("최종 결제가 계산(Coupon.finalPrice)")
    class FinalPrice {

        @Test
        @DisplayName("PERCENT 쿠폰의 최종가")
        void percent() {
            Coupon coupon = Coupon.create("정률 쿠폰", 100L, DiscountType.PERCENT, 10L);

            assertThat(coupon.finalPrice(10000L)).isEqualTo(9000L);
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
