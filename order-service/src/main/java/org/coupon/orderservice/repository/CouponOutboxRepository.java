package org.coupon.orderservice.repository;

import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 조회 계약은 {@link ProductOutboxRepository}와 같다. 근거도 그쪽 javadoc을 따른다.
 */
public interface CouponOutboxRepository extends JpaRepository<CouponOutbox, UUID> {

    Optional<CouponOutbox> findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
            String type, UUID sagaId, CouponOrderStatus requestStatus, List<SagaStatus> sagaStatuses);

    List<CouponOutbox> findByTypeAndOutboxStatusAndSagaStatusIn(
            String type, OutboxStatus outboxStatus, List<SagaStatus> sagaStatuses);

    /** 사가가 끝났을 때 재고 Outbox와 함께 상태를 맞춰야 하므로 sagaId로도 찾는다. */
    Optional<CouponOutbox> findByTypeAndSagaIdAndRequestStatus(
            String type, UUID sagaId, CouponOrderStatus requestStatus);
}
