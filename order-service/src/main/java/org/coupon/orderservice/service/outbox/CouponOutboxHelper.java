package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.coupon.orderservice.repository.CouponOutboxRepository;
import org.coupon.orderservice.saga.SagaTraceTag;
import org.coupon.orderservice.saga.SagaTypes;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CouponOutboxHelper {

    private static final List<SagaStatus> PUBLISHABLE =
            List.of(SagaStatus.STARTED, SagaStatus.COMPENSATING);

    private final CouponOutboxRepository couponOutboxRepository;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final SagaTraceTag sagaTraceTag;

    public CouponOutbox saveCouponOutboxMessage(Order order, CouponOrderStatus requestStatus,
                                                SagaStatus sagaStatus) {
        UUID messageId = UUID.randomUUID();

        CouponRequest request = new CouponRequest(
                messageId,
                order.getSagaId(),
                order.getId(),
                order.getUserId(),
                order.getCouponId(),
                order.getTotalAmount(),
                requestStatus,
                Instant.now());

        try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_WRITE, order.getSagaId())) {
            return couponOutboxRepository.save(CouponOutbox.builder()
                    .id(messageId)
                    .sagaId(order.getSagaId())
                    .orderId(order.getId())
                    .type(SagaTypes.ORDER_PROCESSING)
                    .payload(sagaPayloadCodec.serialize(request))
                    .orderStatus(order.getStatus())
                    .sagaStatus(sagaStatus)
                    .requestStatus(requestStatus)
                    .build());
        }
    }

    public Optional<CouponOutbox> findAwaiting(UUID sagaId, CouponOrderStatus requestStatus, SagaStatus expected) {
        return couponOutboxRepository.findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus, List.of(expected));
    }

    public Optional<CouponOutbox> find(UUID sagaId, CouponOrderStatus requestStatus) {
        return couponOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    public List<CouponOutbox> findUnpublished() {
        return couponOutboxRepository.findByTypeAndOutboxStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING, OutboxStatus.STARTED, PUBLISHABLE);
    }

    public List<CouponOutbox> findTerminal() {
        return couponOutboxRepository.findByTypeAndOutboxStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING,
                OutboxStatus.COMPLETED,
                List.of(SagaStatus.SUCCEEDED, SagaStatus.COMPENSATED, SagaStatus.FAILED));
    }

    public void delete(List<CouponOutbox> outboxes) {
        couponOutboxRepository.deleteAllInBatch(outboxes);
    }

    public Optional<CouponOutbox> findById(UUID outboxId) {
        return couponOutboxRepository.findById(outboxId);
    }

    public CouponRequest toRequest(CouponOutbox outbox) {
        return sagaPayloadCodec.deserialize(outbox.getPayload(), CouponRequest.class);
    }
}
