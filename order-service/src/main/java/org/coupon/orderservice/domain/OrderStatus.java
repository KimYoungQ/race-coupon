package org.coupon.orderservice.domain;

/**
 * 주문 상태이자 Saga 상태. 별도 SAGA_INSTANCE 테이블 없이 ORDERS row가 그대로 사가 인스턴스다.
 *
 * <p>쿠폰 사용이 사가의 마지막 단계라 성공 시 곧바로 {@link #COMPLETED}가 된다.
 * 값이 바뀌지 않는 중간 상태(COUPON_APPLIED)는 두지 않는다.
 */
public enum OrderStatus {

    CREATED,
    STOCK_RESERVED,
    COMPLETED,
    COMPENSATING,
    FAILED
}
