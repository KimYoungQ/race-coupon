package org.coupon.couponservice.exception;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;

/**
 * 이미 사용된 쿠폰을 다시 쓰려 한 경우.
 *
 * <p>사가에서는 <b>비즈니스 실패</b>이지 트랜잭션 실패가 아니다. 참여자 서비스가 이 예외를 잡아
 * {@code CouponStatus.FAILED} 응답으로 바꿔야 한다 — 그대로 던지면 트랜잭션이 롤백되면서
 * 응답 Outbox까지 사라지고, 오케스트레이터는 응답을 영영 받지 못한 채 멈춘다.
 */
public class CouponAlreadyUsedException extends BusinessException {

    public CouponAlreadyUsedException(Long issuedCouponId) {
        super(ErrorCode.COUPON_ALREADY_USED, "이미 사용된 쿠폰입니다: issuedCouponId=" + issuedCouponId);
    }
}
