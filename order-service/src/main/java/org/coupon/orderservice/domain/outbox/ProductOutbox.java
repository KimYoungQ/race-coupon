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
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.OrderStatus;

import java.util.UUID;

@Getter
@Entity
@Table(name = "product_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_outbox_saga_request",
                columnNames = {"saga_id", "request_status"}),
        indexes = @Index(
                name = "idx_product_outbox_poll",
                columnList = "type, outbox_status, saga_status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOutbox extends OrchestratorOutbox {

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false)
    private StockOrderStatus requestStatus;

    @Builder
    private ProductOutbox(UUID id, UUID sagaId, Long orderId, String type, String payload,
                          OrderStatus orderStatus, SagaStatus sagaStatus, StockOrderStatus requestStatus) {
        super(id, sagaId, orderId, type, payload, orderStatus, sagaStatus);
        this.requestStatus = requestStatus;
    }
}
