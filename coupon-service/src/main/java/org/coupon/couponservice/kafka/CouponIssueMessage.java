package org.coupon.couponservice.kafka;

public record CouponIssueMessage(Long couponId, Long userId) {

    public static final String TOPIC = "coupon-issue";
}
