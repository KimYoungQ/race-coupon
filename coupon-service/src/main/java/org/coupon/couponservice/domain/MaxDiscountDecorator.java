package org.coupon.couponservice.domain;

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
