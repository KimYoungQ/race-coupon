package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponSoldOutException extends BusinessException {

    public CouponSoldOutException() {
        super(ErrorCode.COUPON_SOLD_OUT);
    }
}
