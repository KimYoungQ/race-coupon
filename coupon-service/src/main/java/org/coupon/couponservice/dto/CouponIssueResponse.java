package org.coupon.couponservice.dto;

public record CouponIssueResponse(Long couponId, Long issuedQuantity, Long remaining) {
}
