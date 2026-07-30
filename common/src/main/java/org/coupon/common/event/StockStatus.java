package org.coupon.common.event;

/**
 * 재고 처리 결과. 성공·실패·복구를 같은 {@code product-response} 토픽 안에서 구분한다.
 * listener는 토픽이 아니라 이 값으로 분기한다.
 *
 * <p>{@link #FAILED}가 예약 실패인지 복구 실패인지는 응답만으로 알 수 없다.
 * 오케스트레이터가 {@code product_outbox}의 SagaStatus와 함께 판별한다 —
 * {@code STARTED + FAILED}면 예약 실패(되돌릴 것 없음),
 * {@code COMPENSATING + FAILED}면 복구 실패(자동 회복 불가)다.
 */
public enum StockStatus {

    /** 재고 예약 성공. */
    RESERVED,

    /** 재고 복구 성공. */
    RESTORED,

    /** 재고 예약 또는 복구 실패. */
    FAILED
}
