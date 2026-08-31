package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponNotOwnedException extends BusinessException {

    public CouponNotOwnedException(Long issuedCouponId) {
        super(ErrorCode.COUPON_NOT_OWNED, "본인의 쿠폰이 아닙니다: issuedCouponId=" + issuedCouponId);
    }
}
