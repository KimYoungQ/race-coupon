package org.coupon.racecoupon.exception;

public class CouponNotFoundException extends BusinessException {

    public CouponNotFoundException(Long couponId) {
        super(ErrorCode.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다: " + couponId);
    }
}
