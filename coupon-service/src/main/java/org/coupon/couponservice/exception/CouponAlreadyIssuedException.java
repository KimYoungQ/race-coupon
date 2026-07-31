package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

/**
 * 이미 이 쿠폰을 발급받은 사용자가 다시 요청한 경우. 1인 1매 위반이다.
 *
 * <p>Redis 발급자 집합(SISMEMBER)에 걸렸다는 것만으로 이 예외를 던지면 안 된다.
 * 발급 메시지 발행이 실패했던 사용자는 집합에는 있지만 {@code IssuedCoupon} row가 없는데,
 * 그 상태에서 이 예외를 던지면 재시도할 길이 막혀 발급이 영구 유실된다.
 * 반드시 row 존재를 확인한 뒤에만 쓴다 — 없으면 재발행이 맞다.
 *
 * <p>"아직 반영되지 않았다"({@link CouponNotIssuedYetException})와는 다른 상태다.
 * 그쪽은 발급이 성립했고 반영만 늦은 것이고, 이쪽은 발급이 이미 완료된 것이다.
 */
public class CouponAlreadyIssuedException extends BusinessException {

    public CouponAlreadyIssuedException(Long userId, Long couponId) {
        super(ErrorCode.COUPON_ALREADY_ISSUED,
                "이미 발급받은 쿠폰입니다: userId=" + userId + ", couponId=" + couponId);
    }
}
