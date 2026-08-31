package org.coupon.couponservice.repository;

import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.couponservice.domain.outbox.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    Optional<OrderOutbox> findByTypeAndSagaIdAndRequestStatus(
            String type, UUID sagaId, CouponOrderStatus requestStatus);

    List<OrderOutbox> findByTypeAndOutboxStatus(String type, OutboxStatus outboxStatus);
}
