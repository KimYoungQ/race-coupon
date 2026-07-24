package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class InvalidDiscountException extends BusinessException {

    public InvalidDiscountException() {
        super(ErrorCode.INVALID_DISCOUNT);
    }
}
