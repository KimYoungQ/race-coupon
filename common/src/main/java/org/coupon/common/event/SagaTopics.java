package org.coupon.common.event;

/**
 * Saga 토픽 상수. 토픽은 <b>정확히 네 개</b>이며, 명령이나 처리 결과가 아니라
 * 서비스 사이의 방향(request/response 채널)을 기준으로 나뉜다.
 *
 * <p>정상 실행과 보상 실행은 같은 request 토픽에서 요청 상태 enum으로 구분하고,
 * 성공·실패·복구는 같은 response 토픽에서 결과 enum으로 구분한다.
 * 토픽을 쪼개면 같은 자원에 대한 정방향/보상 메시지가 서로 다른 topic-partition으로 흩어져
 * 다른 컨슈머 스레드에서 동시 실행되고, 락 없이 갱신되는 row에 lost update가 생긴다.
 *
 * <p>토픽 상수를 DTO마다 흩뿌리지 않고 여기 한 곳에 모은다.
 */
public final class SagaTopics {

    /** order-service → product-service. 재고 예약·복구 요청. key = productId */
    public static final String PRODUCT_REQUEST = "product-request";

    /** product-service → order-service. 재고 처리 결과. key = orderId */
    public static final String PRODUCT_RESPONSE = "product-response";

    /** order-service → coupon-service. 쿠폰 적용·복구 요청. key = userId:couponId */
    public static final String COUPON_REQUEST = "coupon-request";

    /** coupon-service → order-service. 쿠폰 처리 결과. key = orderId */
    public static final String COUPON_RESPONSE = "coupon-response";

    private SagaTopics() {
    }
}
