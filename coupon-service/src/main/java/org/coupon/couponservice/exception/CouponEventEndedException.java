package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

/**
 * 발급 이벤트가 이미 종료된 경우.
 *
 * <p>수량이 남아 있어도 거절한다. 종료 시각이 지나면 Redis 키의 TTL도 0 이하가 되는데,
 * 그 값을 그대로 EXPIRE에 넘기면 Redis가 키를 즉시 삭제해 카운터가 0부터 다시 시작한다.
 * 여기서 먼저 끊어야 그 경로로 들어가지 않는다.
 */
public class CouponEventEndedException extends BusinessException {

    public CouponEventEndedException(Long couponId) {
        super(ErrorCode.COUPON_EVENT_ENDED, "종료된 이벤트입니다: couponId=" + couponId);
    }
}
