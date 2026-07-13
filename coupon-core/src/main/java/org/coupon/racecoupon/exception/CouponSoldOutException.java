package org.coupon.racecoupon.exception;

public class CouponSoldOutException extends BusinessException {

    public CouponSoldOutException() {
        super(ErrorCode.COUPON_SOLD_OUT);
    }
}
