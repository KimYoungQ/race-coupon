package org.coupon.orderservice.repository;

import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductOutboxRepository extends JpaRepository<ProductOutbox, UUID> {

    Optional<ProductOutbox> findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
            String type, UUID sagaId, StockOrderStatus requestStatus, List<SagaStatus> sagaStatuses);

    Optional<ProductOutbox> findByTypeAndSagaIdAndRequestStatus(
            String type, UUID sagaId, StockOrderStatus requestStatus);

    List<ProductOutbox> findByTypeAndOutboxStatusAndSagaStatusIn(
            String type, OutboxStatus outboxStatus, List<SagaStatus> sagaStatuses);
}
