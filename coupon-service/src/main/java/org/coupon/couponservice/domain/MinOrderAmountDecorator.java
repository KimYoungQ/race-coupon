package org.coupon.couponservice.domain;

public class MinOrderAmountDecorator implements DiscountPolicy {

    private final DiscountPolicy delegate;
    private final long minOrderAmount;

    public MinOrderAmountDecorator(DiscountPolicy delegate, long minOrderAmount) {
        this.delegate = delegate;
        this.minOrderAmount = minOrderAmount;
    }

    @Override
    public long discount(long price) {
        return price < minOrderAmount ? 0 : delegate.discount(price);
    }
}
