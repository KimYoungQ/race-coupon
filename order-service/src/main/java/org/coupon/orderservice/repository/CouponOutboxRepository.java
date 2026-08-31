package org.coupon.orderservice.repository;

import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponOutboxRepository extends JpaRepository<CouponOutbox, UUID> {

    Optional<CouponOutbox> findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
            String type, UUID sagaId, CouponOrderStatus requestStatus, List<SagaStatus> sagaStatuses);

    List<CouponOutbox> findByTypeAndOutboxStatusAndSagaStatusIn(
            String type, OutboxStatus outboxStatus, List<SagaStatus> sagaStatuses);

    Optional<CouponOutbox> findByTypeAndSagaIdAndRequestStatus(
            String type, UUID sagaId, CouponOrderStatus requestStatus);
}
