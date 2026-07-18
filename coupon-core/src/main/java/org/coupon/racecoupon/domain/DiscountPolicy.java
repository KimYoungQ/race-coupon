package org.coupon.racecoupon.domain;

/**
 * 할인 계산 전략. 유형별로 원가에서 실제 차감되는 할인액을 계산한다.
 * 구현체는 {@link DiscountType} 상수들이며, 계산식만 다르게 갖는다(전략 패턴).
 */
public interface DiscountPolicy {

    /**
     * 원가에 대해 실제 차감되는 할인액을 계산한다. 반환값은 원가를 초과하지 않는다.
     *
     * @param originalPrice 원가
     * @param discountValue 쿠폰에 설정된 할인 값(PERCENT: %, FIXED_AMOUNT: 원)
     * @return 차감 할인액
     */
    long discount(long originalPrice, long discountValue);
}
