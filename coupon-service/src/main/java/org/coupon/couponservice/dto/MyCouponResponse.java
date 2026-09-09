package org.coupon.couponservice.dto;

import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.domain.IssuedCouponStatus;

import java.time.LocalDateTime;

public record MyCouponResponse(
        Long couponId,
        String title,
        DiscountType discountType,
        Long discountValue,
        Long maxDiscountAmount,
        Long minOrderAmount,
        LocalDateTime eventEndAt,
        IssuedCouponStatus status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt,
        Long orderId
) {
}
