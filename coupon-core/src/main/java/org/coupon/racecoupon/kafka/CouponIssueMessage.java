package org.coupon.racecoupon.kafka;

public record CouponIssueMessage(Long couponId, Long userId) {

    public static final String TOPIC = "coupon-issue";
}
