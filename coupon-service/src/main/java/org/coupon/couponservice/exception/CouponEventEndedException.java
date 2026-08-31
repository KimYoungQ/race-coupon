package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponEventEndedException extends BusinessException {

    public CouponEventEndedException(Long couponId) {
        super(ErrorCode.COUPON_EVENT_ENDED, "종료된 이벤트입니다: couponId=" + couponId);
    }
}
