package org.coupon.common.event;

/**
 * 재고 요청의 종류. 정상 실행과 보상 실행을 같은 {@code product-request} 토픽 안에서 구분한다.
 * food-ordering-system의 {@code payment_request.paymentOrderStatus}와 같은 역할이다.
 *
 * <p>이 값은 Outbox의 {@code request_status} 컬럼에 사본으로 남아
 * 멱등 조회와 UNIQUE 제약이 정상 요청과 보상 요청을 서로 같은 요청으로 오인하지 않게 한다.
 */
public enum StockOrderStatus {

    /** 재고를 예약하라 (= RESERVE). 정상 실행. */
    PENDING,

    /** 기존 재고 예약을 복구하라 (= RESTORE). 보상 실행. */
    CANCELLED
}
