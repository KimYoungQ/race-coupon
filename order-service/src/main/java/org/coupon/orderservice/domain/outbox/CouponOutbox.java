package org.coupon.orderservice.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.OrderStatus;

import java.util.UUID;

/**
 * coupon-service로 나갈 쿠폰 요청의 Outbox. 구조는 {@link ProductOutbox}와 같고
 * {@code requestStatus}의 타입만 다르다.
 *
 * <p>현재 사가에서 이 Outbox에는 {@code PENDING} row만 생긴다 — 쿠폰 적용이 마지막 단계라
 * 그 뒤에 실패할 스텝이 없어 보상 요청을 만들 일이 없다. 그럼에도 UNIQUE에
 * {@code request_status}를 포함해 두는 것은 {@link ProductOutbox}와 계약을 같게 맞춰,
 * 나중에 단계가 추가될 때 여기만 다른 규칙이라 놓치는 일이 없게 하기 위해서다.
 */
@Getter
@Entity
@Table(name = "coupon_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_coupon_outbox_saga_request",
                columnNames = {"saga_id", "request_status"}),
        indexes = @Index(
                name = "idx_coupon_outbox_poll",
                columnList = "type, outbox_status, saga_status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponOutbox extends OrchestratorOutbox {

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false)
    private CouponOrderStatus requestStatus;

    @Builder
    private CouponOutbox(UUID id, UUID sagaId, Long orderId, String type, String payload,
                         OrderStatus orderStatus, SagaStatus sagaStatus, CouponOrderStatus requestStatus) {
        super(id, sagaId, orderId, type, payload, orderStatus, sagaStatus);
        this.requestStatus = requestStatus;
    }
}
