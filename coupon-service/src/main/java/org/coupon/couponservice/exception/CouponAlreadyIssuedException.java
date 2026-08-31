package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponAlreadyIssuedException extends BusinessException {

    public CouponAlreadyIssuedException(Long userId, Long couponId) {
        super(ErrorCode.COUPON_ALREADY_ISSUED,
                "이미 발급받은 쿠폰입니다: userId=" + userId + ", couponId=" + couponId);
    }
}
