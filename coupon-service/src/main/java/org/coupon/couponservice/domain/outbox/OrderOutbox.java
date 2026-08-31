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
    private CouponStatus couponStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false)
    private CouponOrderStatus requestStatus;

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

    public void markPublishFailed() {
        this.outboxStatus = OutboxStatus.STARTED;
    }

    public void markForRepublish() {
        this.outboxStatus = OutboxStatus.STARTED;
        this.processedAt = null;
    }

    public void recordPublishAttempt() {
        this.publishAttempts++;
    }
}
