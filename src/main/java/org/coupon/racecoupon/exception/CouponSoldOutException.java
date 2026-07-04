package org.coupon.racecoupon.exception;

/**
 * 쿠폰 재고가 모두 소진된 상태에서 발급을 시도할 때 발생한다.
 * Coupon.issue()가 던진다. (C7에서 BusinessException 상속 + 409 응답으로 승격 예정)
 */
public class CouponSoldOutException extends RuntimeException {

    private static final String MESSAGE = "쿠폰이 모두 소진되었습니다";

    public CouponSoldOutException() {
        super(MESSAGE);
    }
}
