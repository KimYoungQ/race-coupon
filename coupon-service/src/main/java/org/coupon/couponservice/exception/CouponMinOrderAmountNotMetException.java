package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

/**
 * 최소 주문 금액을 채우지 못한 채 쿠폰을 적용하려 한 경우.
 *
 * <p>{@code MinOrderAmountDecorator}는 같은 상황에서 예외 없이 할인 0을 돌려주지만,
 * 그 동작은 "이 쿠폰을 쓰면 얼마인가" 조회용이다. 사가에서 조용히 할인 0으로 완료되면
 * 사용자는 이유를 모르는데 쿠폰만 소진되므로 여기서는 명시적으로 실패시킨다.
 */
public class CouponMinOrderAmountNotMetException extends BusinessException {

    public CouponMinOrderAmountNotMetException(Long minOrderAmount, Long orderAmount) {
        super(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET,
                "최소 주문 금액을 충족하지 않습니다: 필요=" + minOrderAmount + ", 주문=" + orderAmount);
    }
}
