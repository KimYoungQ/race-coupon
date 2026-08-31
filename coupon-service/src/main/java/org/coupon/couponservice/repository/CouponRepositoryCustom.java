package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.Coupon;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepositoryCustom {

    long increaseIssuedQuantity(Long couponId);

    List<Coupon> findIssuableBy(Long userId, LocalDateTime now);

}
