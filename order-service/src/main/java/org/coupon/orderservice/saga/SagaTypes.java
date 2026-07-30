package org.coupon.orderservice.saga;

/**
 * Outbox의 {@code type} 컬럼 값.
 *
 * <p>지금은 사가가 한 종류뿐이라 값도 하나지만 컬럼을 없애지 않는다.
 * 폴링 인덱스 {@code (type, outbox_status, saga_status)}의 선두 컬럼이고,
 * 사가가 늘어날 때 인덱스와 조회 조건을 함께 고치는 것보다 처음부터 두는 편이 싸다.
 */
public final class SagaTypes {

    public static final String ORDER_PROCESSING = "OrderProcessingSaga";

    private SagaTypes() {
    }
}
