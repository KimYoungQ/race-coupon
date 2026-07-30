package org.coupon.orderservice.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.OrderStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 오케스트레이터가 소유하는 Outbox의 공통 뼈대.
 *
 * <p>order-service는 사가 상태 머신의 주인이라 발행 여부({@link OutboxStatus})와
 * 사가 진행도({@link SagaStatus})를 <b>둘 다</b> 갖는다. 두 축은 직교한다 —
 * "메시지를 보냈는가"와 "이 단계가 어디까지 갔는가"는 다른 질문이다.
 * 참여자 서비스의 Outbox에는 {@code SagaStatus}가 없다.
 *
 * <p>{@code requestStatus}는 여기 두지 않는다. 재고와 쿠폰이 서로 다른 enum을 쓰기 때문이며,
 * 각 하위 클래스가 자기 타입으로 선언한다. 이 값이 <b>정상 요청과 보상 요청을 구분하는 유일한 기준</b>이라
 * UNIQUE 제약과 멱등 조회에 반드시 포함돼야 한다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OrchestratorOutbox {

    /**
     * 메시지 식별자. DB가 만들어주는 시퀀스가 아니라 애플리케이션이 발급한 UUID를 그대로 PK로 쓴다 —
     * payload에 실려 나가는 {@code id}와 같은 값이어야 로그에서 양쪽을 이어 볼 수 있다.
     */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    /** 사가 상관 키. Outbox 조회의 주 키다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private UUID sagaId;

    /** 도메인 조회 키. sagaId와 1:1이지만 사람이 SQL로 좇을 때는 이쪽이 편하다. */
    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 발행 완료 시각. 재발행 판정과 지연 진단에 쓴다. */
    private LocalDateTime processedAt;

    /** 사가 종류. 지금은 한 종류뿐이지만 폴링 인덱스의 선두 컬럼이라 남겨 둔다. */
    @Column(nullable = false)
    private String type;

    /**
     * 요청 DTO를 직렬화한 JSON. 발행 시점에 record로 되돌려 KafkaTemplate에 넘긴다.
     *
     * <p><b>네이티브 JSON 컬럼 타입을 쓰지 않는다.</b> {@code columnDefinition = "JSON"}으로 두면
     * H2가 이 문자열을 JSON <i>객체</i>가 아니라 JSON <i>문자열 리터럴</i>로 저장해
     * (즉 한 겹 더 따옴표로 감싸) 읽어올 때 역직렬화가 깨진다. MySQL과 H2에서 다르게 동작하는 탓에
     * 운영에서는 멀쩡하고 테스트에서만 깨지는 식으로 나타난다.
     *
     * <p>JSON 컬럼을 포기해도 잃는 것이 없다. payload 안을 SQL로 뒤질 일이 없도록
     * 조회에 필요한 값({@code request_status}, {@code saga_status})은 이미 컬럼으로 꺼내 뒀다.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus outboxStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus sagaStatus;

    /** 이 Outbox를 만든 시점의 주문 상태 스냅샷. 진단용이며 판단 근거로 쓰지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    /** 발행 시도 횟수. 진단용이다 — 상한을 두고 자동 정지시키는 동작은 아직 없다. */
    @Column(nullable = false)
    private int publishAttempts;

    /**
     * 동시 중복 응답 방어선.
     *
     * <p>기대 SagaStatus 조회는 순차 중복만 잡는다. 두 스레드가 같은 응답을 동시에 처리하면
     * 둘 다 "STARTED인 row가 있다"를 읽고 통과하는데(조회는 커밋 전 서로를 못 본다),
     * 커밋 시점에 이 버전이 하나를 떨궈낸다.
     */
    @Version
    private int version;

    protected OrchestratorOutbox(UUID id, UUID sagaId, Long orderId, String type, String payload,
                                 OrderStatus orderStatus, SagaStatus sagaStatus) {
        this.id = id;
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.type = type;
        this.payload = payload;
        this.orderStatus = orderStatus;
        this.sagaStatus = sagaStatus;
        this.outboxStatus = OutboxStatus.STARTED;
        this.createdAt = LocalDateTime.now();
    }

    /** 사가 단계를 전진시킨다. 이 Outbox row를 claim한 스레드만 호출한다. */
    public void advance(OrderStatus orderStatus, SagaStatus sagaStatus) {
        this.orderStatus = orderStatus;
        this.sagaStatus = sagaStatus;
    }

    /** 브로커가 수신을 확인했다. */
    public void markPublished() {
        this.outboxStatus = OutboxStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 발행에 실패했다. STARTED로 되돌려 스케줄러가 다시 집게 한다 —
     * FAILED로 확정해버리면 브로커가 잠깐 흔들렸을 뿐인 메시지가 영영 나가지 못한다.
     */
    public void markPublishFailed() {
        this.outboxStatus = OutboxStatus.STARTED;
    }

    /** 발행을 시도할 때마다 호출한다. */
    public void recordPublishAttempt() {
        this.publishAttempts++;
    }

    /** 종료된 사가인지 — Cleaner가 지워도 되는 대상인지 판정한다. */
    public boolean isTerminal() {
        return sagaStatus == SagaStatus.SUCCEEDED
                || sagaStatus == SagaStatus.COMPENSATED
                || sagaStatus == SagaStatus.FAILED;
    }
}
