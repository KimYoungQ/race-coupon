package org.coupon.couponservice.dto;

import org.coupon.couponservice.domain.DiscountType;

import java.time.LocalDateTime;

public record CouponResponse(
        Long couponId,
        String title,
        Long totalQuantity,
        Long issuedQuantity,
        DiscountType discountType,
        Long discountValue,
        Long maxDiscountAmount,
        Long minOrderAmount,
        LocalDateTime eventEndAt
) {
}
