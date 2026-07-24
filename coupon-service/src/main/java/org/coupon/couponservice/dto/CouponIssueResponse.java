package org.coupon.couponservice.dto;

import org.coupon.couponservice.domain.Coupon;

public record CouponIssueResponse(Long couponId, Long issuedQuantity, Long remaining) {

    public static CouponIssueResponse from(Coupon coupon) {
        return new CouponIssueResponse(coupon.getId(), coupon.getIssuedQuantity(), coupon.remaining());
    }
}
