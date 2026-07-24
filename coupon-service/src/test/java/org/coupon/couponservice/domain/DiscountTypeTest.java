package org.coupon.couponservice.domain;

import org.coupon.couponservice.exception.InvalidDiscountException;
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
}
