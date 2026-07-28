package org.coupon.common.event;

public record CouponRestoreCommand(Long orderId, Long userId, Long issuedCouponId) {

    public static final String TOPIC = "coupon-restore";
}
