package org.coupon.common.event;

public record CouponApplyRequestPayload(
        Long orderId,
        Long userId,
        Long couponId,
        Long orderAmount,
        RequestType type
) {
}
