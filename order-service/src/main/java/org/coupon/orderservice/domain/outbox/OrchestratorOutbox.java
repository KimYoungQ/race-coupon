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

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OrchestratorOutbox {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

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

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus outboxStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus sagaStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private int publishAttempts;

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

    public void advance(OrderStatus orderStatus, SagaStatus sagaStatus) {
        this.orderStatus = orderStatus;
        this.sagaStatus = sagaStatus;
    }

    public void markPublished() {
        this.outboxStatus = OutboxStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void markPublishFailed() {
        this.outboxStatus = OutboxStatus.STARTED;
    }

    public void recordPublishAttempt() {
        this.publishAttempts++;
    }

    public boolean isTerminal() {
        return sagaStatus == SagaStatus.SUCCEEDED
                || sagaStatus == SagaStatus.COMPENSATED
                || sagaStatus == SagaStatus.FAILED;
    }
}
