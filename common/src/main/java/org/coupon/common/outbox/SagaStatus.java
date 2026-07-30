package org.coupon.common.outbox;

/**
 * Outbox row가 담당하는 Saga leg의 진행도. {@link OutboxStatus}(발행 여부)와 축이 직교한다.
 *
 * <p><b>오케스트레이터(order-service)의 Outbox만 이 값을 갖는다.</b>
 * 참여자는 전체 Saga 상태 머신의 주인이 아니라 자기가 무엇을 했는지만 알므로
 * 참여자 Outbox에는 이 컬럼이 없다.
 *
 * <p>멱등성의 1차 방어선이 이 값이다. 응답을 받으면
 * {@code sagaId + requestStatus + 기대 SagaStatus}로 Outbox를 조회하고,
 * 비어 있으면 이미 처리된 응답이므로 조용히 무시한다.
 */
public enum SagaStatus {

    /** 요청을 냈고 응답을 기다리는 중. */
    STARTED,

    /** 이 leg는 성공했고 사가가 다음 단계로 진행 중. */
    PROCESSING,

    /** 사가 전체 성공. */
    SUCCEEDED,

    /** 이 leg가 실패해 보상이 필요하다고 판정됨. */
    FAILED,

    /** 보상 요청을 냈고 응답을 기다리는 중. */
    COMPENSATING,

    /** 보상 완료. */
    COMPENSATED
}
