package org.coupon.racecoupon.domain;

import org.coupon.racecoupon.exception.InvalidDiscountException;

/**
 * 쿠폰 할인 유형. 타입별 검증 불변식과 할인 계산식을 상수마다 다형적으로 정의한다(switch 분기 없이 OCP 유지).
 * 각 상수가 {@link DiscountPolicy} 구현체(전략)다. PERCENT는 정률(%), FIXED_AMOUNT는 정액(원 차감).
 */
public enum DiscountType implements DiscountPolicy {

    PERCENT {
        @Override
        public void validate(long value) {
            if (value < 1 || value > 100) {
                throw new InvalidDiscountException();
            }
        }

        @Override
        public long discount(long originalPrice, long discountValue) {
            return originalPrice * discountValue / 100; // 정수 floor
        }
    },

    FIXED_AMOUNT {
        @Override
        public void validate(long value) {
            if (value <= 0) {
                throw new InvalidDiscountException();
            }
        }

        @Override
        public long discount(long originalPrice, long discountValue) {
            return Math.min(originalPrice, discountValue); // 원가 초과분은 원가로 상한
        }
    };

    /**
     * 할인 값이 이 유형의 불변식을 만족하는지 검증한다.
     *
     * @throws InvalidDiscountException 유형별 허용 범위를 벗어난 경우
     */
    public abstract void validate(long value);
}
