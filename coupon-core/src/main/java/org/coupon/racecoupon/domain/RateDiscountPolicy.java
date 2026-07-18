package org.coupon.racecoupon.domain;

/**
 * 정률 할인 전략. 원가의 {@code rate}%를 할인액으로 계산한다.
 */
public class RateDiscountPolicy implements DiscountPolicy {

    private final long rate; // 1~100

    public RateDiscountPolicy(long rate) {
        this.rate = rate;
    }

    @Override
    public long discount(long price) {
        return price * rate / 100; // 정수 floor
    }
}
