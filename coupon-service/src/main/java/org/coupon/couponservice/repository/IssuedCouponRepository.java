package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, Long>, IssuedCouponRepositoryCustom {

    Optional<IssuedCoupon> findByUserIdAndCouponIdAndStatus(Long userId, Long couponId, IssuedCouponStatus status);

    Optional<IssuedCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
}
