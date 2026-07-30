package org.coupon.common.event;

/**
 * 쿠폰 요청의 종류. 정상 적용과 보상 복구를 같은 {@code coupon-request} 토픽 안에서 구분한다.
 *
 * <p><b>{@link #CANCELLED}는 계약에 존재하되 현재 Saga에서는 발행되지 않는다.</b>
 * 쿠폰 적용이 마지막 단계라 그 뒤에 실패할 스텝이 없고, 명시적인 {@code CouponStatus.FAILED}는
 * "적용 실패가 확정됐다"는 뜻이라 되돌릴 것이 없기 때문이다.
 * 실제로 필요해지는 경우는 (1) 적용 결과가 timeout으로 UNKNOWN이거나
 * (2) 적용 뒤에 새 Saga 단계가 생겨 그것이 실패할 때 둘뿐이며, 둘 다 현재 범위 밖이다.
 */
public enum CouponOrderStatus {

    /** 쿠폰을 적용하라 (= APPLY). 정상 실행. */
    PENDING,

    /** 기존 쿠폰 적용을 복구하라 (= RESTORE). 보상 실행. */
    CANCELLED
}
