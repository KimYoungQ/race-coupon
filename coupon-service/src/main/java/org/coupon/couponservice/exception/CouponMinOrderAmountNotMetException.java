package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

public class CouponMinOrderAmountNotMetException extends BusinessException {

    public CouponMinOrderAmountNotMetException(Long minOrderAmount, Long orderAmount) {
        super(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET,
                "최소 주문 금액을 충족하지 않습니다: 필요=" + minOrderAmount + ", 주문=" + orderAmount);
    }
}
