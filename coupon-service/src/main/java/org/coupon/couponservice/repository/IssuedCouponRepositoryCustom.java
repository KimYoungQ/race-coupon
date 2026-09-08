package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.IssuedCouponStatus;

import java.time.LocalDateTime;

public interface IssuedCouponRepositoryCustom {

    long countByCouponId(Long couponId);

    IssuedCouponStatus findStatusBy(Long userId, Long couponId);

    long markUsed(Long userId, Long couponId, Long orderId, LocalDateTime usedAt);

    long restore(Long userId, Long couponId, Long orderId);
}
