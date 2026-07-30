package org.coupon.couponservice.domain;

/**
 * 발급된 쿠폰의 사용 여부.
 *
 * <p>주문 사가가 쿠폰을 적용할 때 {@code (userId, couponId, ISSUED)}로 대상을 찾으므로,
 * 이 값이 곧 "아직 쓸 수 있는 쿠폰인가"의 판정 기준이다.
 */
public enum IssuedCouponStatus {

    /** 발급됐고 아직 사용되지 않음. 사가가 적용할 수 있는 상태. */
    ISSUED,

    /** 어떤 주문에 사용됨. */
    USED
}
