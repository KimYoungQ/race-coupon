package org.coupon.couponservice.domain;

public interface DiscountPolicy {

    long discount(long price);
}
