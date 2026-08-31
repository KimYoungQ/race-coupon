package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponAlreadyUsedException extends BusinessException {

    public CouponAlreadyUsedException(Long issuedCouponId) {
        super(ErrorCode.COUPON_ALREADY_USED, "이미 사용된 쿠폰입니다: issuedCouponId=" + issuedCouponId);
    }
}
