package org.coupon.couponservice.dto;

import org.coupon.couponservice.domain.DiscountType;

/**
 * 쿠폰 상세. 발급 결과만 담는 {@link CouponIssueResponse}와 달리 등록된 쿠폰의 전체 모습을 보여준다.
 */
public record CouponResponse(
        Long couponId,
        String title,
        Long totalQuantity,
        Long issuedQuantity,
        DiscountType discountType,
        Long discountValue,
        Long maxDiscountAmount,
        Long minOrderAmount
) {
}
