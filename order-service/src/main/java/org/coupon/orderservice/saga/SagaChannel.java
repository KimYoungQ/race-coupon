package org.coupon.orderservice.saga;

/**
 * 오케스트레이터가 요청을 내보내는 채널. Outbox 종류와 1:1로 대응한다.
 *
 * <p>발행 상태 갱신이 두 Outbox에 대해 같은 모양이라 분기 키로 쓴다.
 * 채널이 셋 이상으로 늘어나면 {@code switch}가 아니라 채널별 컴포넌트로 갈라야 한다.
 */
public enum SagaChannel {

    /** product_outbox → product-request */
    PRODUCT,

    /** coupon_outbox → coupon-request */
    COUPON
}
