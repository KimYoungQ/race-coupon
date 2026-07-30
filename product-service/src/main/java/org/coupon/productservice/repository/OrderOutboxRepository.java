package org.coupon.productservice.repository;

import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.productservice.domain.outbox.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    /**
     * 이 요청을 이미 처리했는지 확인한다.
     *
     * <p>{@code requestStatus}가 빠지면 안 된다. 정상 예약과 보상 복구가 같은 sagaId를 쓰므로,
     * 빼면 복구 요청이 예약 응답을 보고 "이미 처리했다"고 오판한다.
     */
    Optional<OrderOutbox> findByTypeAndSagaIdAndRequestStatus(
            String type, UUID sagaId, StockOrderStatus requestStatus);

    /** 스케줄러 폴링(발행 대상)과 Cleaner(정리 대상)가 함께 쓴다. */
    List<OrderOutbox> findByTypeAndOutboxStatus(String type, OutboxStatus outboxStatus);
}
