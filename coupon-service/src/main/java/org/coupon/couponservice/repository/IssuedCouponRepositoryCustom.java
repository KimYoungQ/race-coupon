package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.dto.MyCouponResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface IssuedCouponRepositoryCustom {

    long countByCouponId(Long couponId);

    List<MyCouponResponse> findMyCoupons(Long userId);

    IssuedCouponStatus findStatusBy(Long userId, Long couponId);

    long markUsed(Long userId, Long couponId, Long orderId, LocalDateTime usedAt);

    long restore(Long userId, Long couponId, Long orderId);
}
