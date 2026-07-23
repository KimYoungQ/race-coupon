package org.coupon.userservice.exception;

import org.coupon.racecoupon.exception.BusinessException;
import org.coupon.racecoupon.exception.ErrorCode;

/**
 * 아이디 오류와 비밀번호 오류를 구분하지 않는다 — 어느 쪽이 틀렸는지 알려주면
 * 계정 존재 여부가 노출되어 계정 열거(enumeration) 공격에 취약해진다.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}
