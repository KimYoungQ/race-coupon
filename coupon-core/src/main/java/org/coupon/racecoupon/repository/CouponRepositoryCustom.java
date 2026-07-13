package org.coupon.racecoupon.repository;

import org.coupon.racecoupon.domain.Coupon;

import java.util.Optional;

public interface CouponRepositoryCustom {

    Optional<Coupon> findWithPessimisticLockById(Long id);
}
