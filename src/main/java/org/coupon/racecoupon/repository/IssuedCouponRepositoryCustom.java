package org.coupon.racecoupon.repository;

public interface IssuedCouponRepositoryCustom {

    /**
     * 특정 쿠폰의 실제 발급 건수. 동시성 테스트에서 발급 개수 검증에 사용한다.
     */
    long countByCouponId(Long couponId);
}
