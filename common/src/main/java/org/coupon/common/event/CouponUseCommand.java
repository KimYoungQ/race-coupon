package org.coupon.common.event;

public record CouponUseCommand(Long orderId, Long userId, Long issuedCouponId, Long orderAmount) {

    public static final String TOPIC = "coupon-use";
}
