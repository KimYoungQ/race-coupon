package org.coupon.productservice.saga;

/**
 * Outbox의 {@code type} 컬럼 값. order-service와 같은 문자열을 쓴다 —
 * 같은 사가에 속한 요청과 응답이 서로 다른 이름으로 기록되면 양쪽 로그를 맞춰 볼 수 없다.
 */
public final class SagaTypes {

    public static final String ORDER_PROCESSING = "OrderProcessingSaga";

    private SagaTypes() {
    }
}
