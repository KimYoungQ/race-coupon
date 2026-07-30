package org.coupon.couponservice.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * order-service로 돌려보낼 쿠폰 처리 결과의 Outbox. 이름은 <b>상대 서비스</b> 기준이다.
 *
 * <p><b>{@code SagaStatus}와 {@code OrderStatus} 컬럼이 없다.</b> 참여자는 전체 사가 상태 머신의
 * 주인이 아니라 자기가 무엇을 했는지만 안다. 사가가 몇 번째 단계인지는 오케스트레이터가 판단할 몫이고,
 * 여기서 흉내내면 진실의 원천이 둘로 갈라진다.
 *
 * <p>{@code UNIQUE(saga_id, request_status)}는 같은 요청을 두 번 받아도 결과가 두 벌 생기지 않게 하고,
 * 동시에 정상 적용 응답과 보상 복구 응답이 같은 sagaId 아래 공존할 수 있게 한다.
 * 지금 사가에서는 보상 요청이 발행되지 않지만(§4.3) 계약을 product 쪽과 같게 맞춰 둔다 —
 * 나중에 단계가 추가될 때 여기만 다른 규칙이라 놓치는 일이 없도록.
 */
@Getter
@Entity
@Table(name = "order_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_outbox_saga_request",
                columnNames = {"saga_id", "request_status"}),
        indexes = @Index(
                name = "idx_order_outbox_poll",
                columnList = "type, outbox_status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutbox {

    /** 응답 메시지 식별자. payload 안의 {@code id}와 같은 값이라 로그로 양쪽을 이어 볼 수 있다. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    /** 사가 상관 키. 요청에서 받은 값을 그대로 되돌려준다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private UUID sagaId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    @Column(nullable = false)
    private String type;

    /**
     * 응답 DTO를 직렬화한 JSON.
     *
     * <p>네이티브 JSON 컬럼 타입을 쓰지 않는다 — H2가 이 문자열을 JSON 객체가 아니라
     * JSON 문자열 리터럴로 저장해(따옴표를 한 겹 더 씌워) 읽어올 때 역직렬화가 깨진다.
     * MySQL에서는 멀쩡하고 테스트에서만 깨지는 식으로 나타난다.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus outboxStatus;

    /** 참여자가 실제로 무엇을 했는지. 오케스트레이터의 SagaStatus와는 다른 축이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus couponStatus;

    /** 이 응답이 어떤 요청에 대한 것인지. 정상 응답과 보상 응답을 구분한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false)
    private CouponOrderStatus requestStatus;

    /** 발행 시도 횟수. 중복 요청에 대한 재발행도 포함한다. 진단용이다. */
    @Column(nullable = false)
    private int publishAttempts;

    @Version
    private int version;

    @Builder
    private OrderOutbox(UUID id, UUID sagaId, Long orderId, String type, String payload,
                        CouponStatus couponStatus, CouponOrderStatus requestStatus) {
        this.id = id;
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.type = type;
        this.payload = payload;
        this.couponStatus = couponStatus;
        this.requestStatus = requestStatus;
        this.outboxStatus = OutboxStatus.STARTED;
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.outboxStatus = OutboxStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 발행 실패. STARTED로 되돌려 다음 폴링이 다시 집게 한다.
     * FAILED로 확정하면 브로커가 잠깐 흔들렸을 뿐인 응답이 영영 못 나가고 사가가 멈춘다.
     */
    public void markPublishFailed() {
        this.outboxStatus = OutboxStatus.STARTED;
    }

    /**
     * 이미 처리한 요청을 다시 받았을 때 <b>같은 응답을 다시 보내도록</b> 표시한다.
     *
     * <p>중복 요청이 왔다는 것은 대개 오케스트레이터가 앞선 응답을 받지 못했다는 뜻이다.
     * 조용히 무시하면(조정자 쪽 멱등 처리와 달리) 사가가 응답을 영영 기다린다.
     * 발행 경로를 스케줄러 하나로 유지하기 위해 여기서 직접 보내지 않고 상태만 되돌린다.
     */
    public void markForRepublish() {
        this.outboxStatus = OutboxStatus.STARTED;
        this.processedAt = null;
    }

    public void recordPublishAttempt() {
        this.publishAttempts++;
    }
}
