package org.coupon.couponservice.domain;

/**
 * 할인 계산 전략. 원가를 받아 차감할 할인액을 계산한다.
 * 구현체({@link RateDiscountPolicy}, {@link FixDiscountPolicy})가 계산식과 필요한 값을 보유한다(전략 패턴).
 */
public interface DiscountPolicy {

    /**
     * 원가에 대해 차감되는 할인액을 계산한다.
     *
     * @param price 원가
     * @return 차감 할인액
     */
    long discount(long price);
}
