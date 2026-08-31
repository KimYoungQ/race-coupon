package org.coupon.productservice.repository;

import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.productservice.domain.outbox.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    Optional<OrderOutbox> findByTypeAndSagaIdAndRequestStatus(
            String type, UUID sagaId, StockOrderStatus requestStatus);

    List<OrderOutbox> findByTypeAndOutboxStatus(String type, OutboxStatus outboxStatus);
}
