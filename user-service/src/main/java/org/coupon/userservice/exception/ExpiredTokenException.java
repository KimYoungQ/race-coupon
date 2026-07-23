package org.coupon.userservice.exception;

import org.coupon.racecoupon.exception.BusinessException;
import org.coupon.racecoupon.exception.ErrorCode;

/**
 * 만료된 토큰. 클라이언트가 이 코드를 보고 Refresh Token으로 재발급을 시도할 수 있도록
 * InvalidTokenException과 구분해서 던진다.
 */
public class ExpiredTokenException extends BusinessException {

    public ExpiredTokenException() {
        super(ErrorCode.TOKEN_EXPIRED);
    }
}
