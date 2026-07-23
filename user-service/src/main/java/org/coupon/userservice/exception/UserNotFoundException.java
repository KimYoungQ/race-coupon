package org.coupon.userservice.exception;

import org.coupon.racecoupon.exception.BusinessException;
import org.coupon.racecoupon.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long userId) {
        super(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: " + userId);
    }
}
