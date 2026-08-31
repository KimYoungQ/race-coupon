package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponNotIssuedYetException extends BusinessException {

    public CouponNotIssuedYetException(Long userId, Long couponId) {
        super(ErrorCode.COUPON_NOT_ISSUED_YET,
                "쿠폰 발급이 아직 반영되지 않았습니다: userId=" + userId + ", couponId=" + couponId);
    }
}
