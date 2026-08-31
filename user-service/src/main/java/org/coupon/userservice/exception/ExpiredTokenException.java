package org.coupon.userservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class ExpiredTokenException extends BusinessException {

    public ExpiredTokenException() {
        super(ErrorCode.TOKEN_EXPIRED);
    }
}
