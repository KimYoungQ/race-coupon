package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponNotFoundException extends BusinessException {

    public CouponNotFoundException(Long couponId) {
        super(ErrorCode.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다: " + couponId);
    }
}
