package org.coupon.orderservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.orderservice.domain.OrderStatus;

public class InvalidOrderStateException extends BusinessException {

    public InvalidOrderStateException(OrderStatus current) {
        super(ErrorCode.INVALID_ORDER_STATE, "현재 주문 상태에서는 처리할 수 없습니다: " + current);
    }
}
