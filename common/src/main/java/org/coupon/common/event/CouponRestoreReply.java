package org.coupon.common.event;

public record CouponRestoreReply(Long orderId, Long issuedCouponId, SagaResult result, String failureCode) {

    public static final String TOPIC = "coupon-restore-reply";
}
