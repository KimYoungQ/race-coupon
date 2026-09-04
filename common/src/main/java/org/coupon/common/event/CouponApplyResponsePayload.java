package org.coupon.common.event;

public record CouponApplyResponsePayload(
        Long orderId,
        Long discountAmount,
        Long finalAmount,
        CouponResult result,
        String failureCode
) {
}
