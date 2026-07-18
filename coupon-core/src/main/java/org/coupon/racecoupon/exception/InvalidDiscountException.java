package org.coupon.racecoupon.exception;

public class InvalidDiscountException extends BusinessException {

    public InvalidDiscountException() {
        super(ErrorCode.INVALID_DISCOUNT);
    }
}
