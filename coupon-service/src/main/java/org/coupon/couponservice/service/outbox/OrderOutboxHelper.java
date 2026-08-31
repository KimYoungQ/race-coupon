package org.coupon.couponservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.CouponStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.couponservice.domain.outbox.OrderOutbox;
import org.coupon.couponservice.repository.OrderOutboxRepository;
import org.coupon.couponservice.saga.SagaTraceTag;
import org.coupon.couponservice.saga.SagaTypes;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderOutboxHelper {

    private final OrderOutboxRepository orderOutboxRepository;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final SagaTraceTag sagaTraceTag;

    public Optional<OrderOutbox> findProcessed(UUID sagaId, CouponOrderStatus requestStatus) {
        return orderOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    public OrderOutbox saveApplied(CouponRequest request, Long issuedCouponId,
                                   Long discountAmount, Long finalAmount) {
        UUID messageId = UUID.randomUUID();
        CouponResponse response = new CouponResponse(
                messageId, request.sagaId(), request.orderId(), request.couponId(), issuedCouponId,
                CouponStatus.APPLIED, discountAmount, finalAmount, List.of(), Instant.now());
        return save(request, response, CouponStatus.APPLIED, messageId);
    }

    public OrderOutbox saveRestored(CouponRequest request, Long issuedCouponId) {
        UUID messageId = UUID.randomUUID();
        CouponResponse response = new CouponResponse(
                messageId, request.sagaId(), request.orderId(), request.couponId(), issuedCouponId,
                CouponStatus.RESTORED, null, null, List.of(), Instant.now());
        return save(request, response, CouponStatus.RESTORED, messageId);
    }

    public OrderOutbox saveFailed(CouponRequest request, Long issuedCouponId, String failureCode) {
        UUID messageId = UUID.randomUUID();
        CouponResponse response = new CouponResponse(
                messageId, request.sagaId(), request.orderId(), request.couponId(), issuedCouponId,
                CouponStatus.FAILED, null, null, List.of(failureCode), Instant.now());
        return save(request, response, CouponStatus.FAILED, messageId);
    }

    private OrderOutbox save(CouponRequest request, CouponResponse response,
                             CouponStatus couponStatus, UUID messageId) {
        try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_WRITE, request.sagaId())) {
            return orderOutboxRepository.save(OrderOutbox.builder()
                    .id(messageId)
                    .sagaId(request.sagaId())
                    .orderId(request.orderId())
                    .type(SagaTypes.ORDER_PROCESSING)
                    .payload(sagaPayloadCodec.serialize(response))
                    .couponStatus(couponStatus)
                    .requestStatus(request.couponOrderStatus())
                    .build());
        }
    }

    public List<OrderOutbox> findUnpublished() {
        return orderOutboxRepository.findByTypeAndOutboxStatus(
                SagaTypes.ORDER_PROCESSING, OutboxStatus.STARTED);
    }

    public Optional<OrderOutbox> findById(UUID outboxId) {
        return orderOutboxRepository.findById(outboxId);
    }

    public CouponResponse toResponse(OrderOutbox outbox) {
        return sagaPayloadCodec.deserialize(outbox.getPayload(), CouponResponse.class);
    }
}
