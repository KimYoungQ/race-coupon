package org.coupon.userservice.exception;

import org.coupon.racecoupon.exception.BusinessException;
import org.coupon.racecoupon.exception.ErrorCode;

/**
 * 서명 불일치·형식 오류·타입 불일치 등 만료 이외의 토큰 오류.
 */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }
}
