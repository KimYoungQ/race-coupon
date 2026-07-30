package org.coupon.common.outbox;

/**
 * Outbox row의 <b>메시지 발행</b> 상태. {@link SagaStatus}(사가 진행도)와 축이 직교한다.
 *
 * <p>도메인 변경과 함께 STARTED로 저장되고, 발행에 성공하면 COMPLETED가 된다.
 * 프로세스가 발행 전에 죽으면 STARTED로 남아 스케줄러가 재발행한다 —
 * 이것이 Outbox 패턴이 사는 지점이다.
 */
public enum OutboxStatus {

    /** 저장됐지만 아직 발행되지 않음. 스케줄러의 재발행 대상. */
    STARTED,

    /** 브로커가 수신을 확인함. */
    COMPLETED,

    /** 발행이 최종 실패함. 사람이 봐야 한다. */
    FAILED
}
