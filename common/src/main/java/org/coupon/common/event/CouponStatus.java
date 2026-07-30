package org.coupon.common.event;

/**
 * 쿠폰 처리 결과. 성공·실패·복구를 같은 {@code coupon-response} 토픽 안에서 구분한다.
 * listener는 토픽이 아니라 이 값으로 분기한다.
 *
 * <p>{@link #FAILED}가 적용 실패인지 복구 실패인지는 {@code coupon_outbox}의 SagaStatus로 판별한다.
 */
public enum CouponStatus {

    /** 쿠폰 적용 성공. */
    APPLIED,

    /** 쿠폰 복구 성공. */
    RESTORED,

    /** 쿠폰 적용 또는 복구 실패. */
    FAILED
}
