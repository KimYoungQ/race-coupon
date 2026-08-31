package org.coupon.couponservice.domain;

public class FixDiscountPolicy implements DiscountPolicy {

    private final long amount;

    public FixDiscountPolicy(long amount) {
        this.amount = amount;
    }

    @Override
    public long discount(long price) {
        return amount;
    }
}
