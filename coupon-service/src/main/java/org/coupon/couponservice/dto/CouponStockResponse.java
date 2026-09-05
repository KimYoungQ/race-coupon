package org.coupon.couponservice.dto;

public record CouponStockResponse(Long couponId, Long issuedQuantity, Long remaining) {
}
