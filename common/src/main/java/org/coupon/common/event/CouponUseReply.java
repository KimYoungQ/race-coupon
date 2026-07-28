package org.coupon.common.event;

public record CouponUseReply(Long orderId, Long issuedCouponId, Long discountAmount, Long finalAmount,
                             SagaResult result, String failureCode) {

    public static final String TOPIC = "coupon-use-reply";
}
