package org.coupon.userservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class DuplicateUsernameException extends BusinessException {

    public DuplicateUsernameException(String username) {
        super(ErrorCode.DUPLICATE_USERNAME, "이미 사용 중인 아이디입니다: " + username);
    }
}
