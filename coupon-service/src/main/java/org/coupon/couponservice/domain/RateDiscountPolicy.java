package org.coupon.couponservice.domain;

public class RateDiscountPolicy implements DiscountPolicy {

    private final long rate;

    public RateDiscountPolicy(long rate) {
        this.rate = rate;
    }

    @Override
    public long discount(long price) {
        return price * rate / 100;
    }
}
