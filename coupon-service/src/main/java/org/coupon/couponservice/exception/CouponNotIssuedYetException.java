package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

/**
 * 주문이 지목한 쿠폰의 발급 row를 아직 찾을 수 없는 경우.
 *
 * <p>발급 API의 201은 "Redis 발급 수량을 확보했고 생성 메시지를 발행 요청했다"는 뜻이지
 * {@code IssuedCoupon} row가 이미 있다는 뜻이 <b>아니다</b>. row는 발급 컨슈머가 나중에 만든다.
 * 그래서 발급 직후 주문하면 이 상태에 걸릴 수 있다.
 *
 * <p>조금 뒤 다시 주문하면 성공할 수 있는 일시적 지연이지만, 현재 사가는 자동 재시도를 두지 않는다 —
 * 재시도하려면 timeout 기반 재평가가 필요하고 그것은 별도 설계 대상이다. 재시도는 사용자가 한다.
 */
public class CouponNotIssuedYetException extends BusinessException {

    public CouponNotIssuedYetException(Long userId, Long couponId) {
        super(ErrorCode.COUPON_NOT_ISSUED_YET,
                "쿠폰 발급이 아직 반영되지 않았습니다: userId=" + userId + ", couponId=" + couponId);
    }
}
