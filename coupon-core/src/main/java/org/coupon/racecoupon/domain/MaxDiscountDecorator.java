package org.coupon.racecoupon.domain;

/**
 * 최대 할인 한도 데코레이터. 감싼 전략의 할인액을 {@code maxDiscount}로 상한 처리한다.
 */
public class MaxDiscountDecorator implements DiscountPolicy {

    private final DiscountPolicy delegate;
    private final long maxDiscount;

    public MaxDiscountDecorator(DiscountPolicy delegate, long maxDiscount) {
        this.delegate = delegate;
        this.maxDiscount = maxDiscount;
    }

    @Override
    public long discount(long price) {
        return Math.min(delegate.discount(price), maxDiscount);
    }
}
