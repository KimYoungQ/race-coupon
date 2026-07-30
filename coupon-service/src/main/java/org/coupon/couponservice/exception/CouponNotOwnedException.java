package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

/**
 * 다른 사용자에게 발급된 쿠폰을 쓰려 한 경우.
 *
 * <p>주문 사가에서는 발생하지 않아야 정상이다 — {@code CouponRequest.userId}가 인증 principal에서
 * 오고 대상도 {@code (userId, couponId)}로 찾으므로 소유자가 어긋날 수 없다.
 * 이 예외가 실제로 떴다면 요청 조립 과정에서 userId의 출처가 바뀌었다는 신호다.
 */
public class CouponNotOwnedException extends BusinessException {

    public CouponNotOwnedException(Long issuedCouponId) {
        super(ErrorCode.COUPON_NOT_OWNED, "본인의 쿠폰이 아닙니다: issuedCouponId=" + issuedCouponId);
    }
}
