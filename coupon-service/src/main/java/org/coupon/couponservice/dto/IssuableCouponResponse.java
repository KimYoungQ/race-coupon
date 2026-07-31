package org.coupon.couponservice.dto;

import org.coupon.couponservice.domain.DiscountType;

import java.time.LocalDateTime;

public record IssuableCouponResponse(
        Long couponId,
        String title,
        Long remaining,
        Long totalQuantity,
        DiscountType discountType,
        Long discountValue,
        Long maxDiscountAmount,
        Long minOrderAmount,
        LocalDateTime eventEndAt
) {
}
