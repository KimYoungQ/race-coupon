package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.Coupon;

import java.util.Optional;

public interface CouponRepositoryCustom {

    Optional<Coupon> findWithPessimisticLockById(Long id);
}
