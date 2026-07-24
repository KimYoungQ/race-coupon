package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.IssuedCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, Long>, IssuedCouponRepositoryCustom {
}
