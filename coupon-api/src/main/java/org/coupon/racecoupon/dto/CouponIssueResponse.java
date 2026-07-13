package org.coupon.racecoupon.dto;

import org.coupon.racecoupon.domain.Coupon;

public record CouponIssueResponse(Long couponId, Long issuedQuantity, Long remaining) {

    public static CouponIssueResponse from(Coupon coupon) {
        return new CouponIssueResponse(coupon.getId(), coupon.getIssuedQuantity(), coupon.remaining());
    }
}
