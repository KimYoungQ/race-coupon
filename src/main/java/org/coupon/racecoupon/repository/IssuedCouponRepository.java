package org.coupon.racecoupon.repository;

import org.coupon.racecoupon.domain.IssuedCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, Long>, IssuedCouponRepositoryCustom {
}
