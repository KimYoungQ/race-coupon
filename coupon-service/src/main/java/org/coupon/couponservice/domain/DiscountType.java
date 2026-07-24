package org.coupon.couponservice.domain;

import org.coupon.couponservice.exception.InvalidDiscountException;

/**
 * 쿠폰 할인 유형(영속 키). 타입별 검증 불변식만 상수마다 다형적으로 정의한다.
 * 할인 계산은 {@link DiscountPolicy} 전략({@link DiscountPolicyFactory}로 선택)이 담당한다.
 * PERCENT는 정률(%), FIXED_AMOUNT는 정액(원 차감).
 */
public enum DiscountType {

    PERCENT {
        @Override
        public void validate(long value) {
            if (value < 1 || value > 100) {
                throw new InvalidDiscountException();
            }
        }
    },

    FIXED_AMOUNT {
        @Override
        public void validate(long value) {
            if (value <= 0) {
                throw new InvalidDiscountException();
            }
        }
    };

    /**
     * 할인 값이 이 유형의 불변식을 만족하는지 검증한다.
     *
     * @throws InvalidDiscountException 유형별 허용 범위를 벗어난 경우
     */
    public abstract void validate(long value);
}
