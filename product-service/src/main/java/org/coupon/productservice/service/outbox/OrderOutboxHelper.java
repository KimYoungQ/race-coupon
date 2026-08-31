package org.coupon.productservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockRequest;
import org.coupon.common.event.StockResponse;
import org.coupon.common.event.StockStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.productservice.domain.outbox.OrderOutbox;
import org.coupon.productservice.repository.OrderOutboxRepository;
import org.coupon.productservice.saga.SagaTraceTag;
import org.coupon.productservice.saga.SagaTypes;
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

    public Optional<OrderOutbox> findProcessed(UUID sagaId, StockOrderStatus requestStatus) {
        return orderOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    public OrderOutbox saveReserved(StockRequest request, String productName, Long unitPrice) {
        UUID messageId = UUID.randomUUID();
        StockResponse response = new StockResponse(
                messageId, request.sagaId(), request.orderId(), request.productId(),
                StockStatus.RESERVED, productName, unitPrice, List.of(), Instant.now());
        return save(request, response, StockStatus.RESERVED, messageId);
    }

    public OrderOutbox saveRestored(StockRequest request) {
        UUID messageId = UUID.randomUUID();
        StockResponse response = new StockResponse(
                messageId, request.sagaId(), request.orderId(), request.productId(),
                StockStatus.RESTORED, null, null, List.of(), Instant.now());
        return save(request, response, StockStatus.RESTORED, messageId);
    }

    public OrderOutbox saveFailed(StockRequest request, String failureCode) {
        UUID messageId = UUID.randomUUID();
        StockResponse response = new StockResponse(
                messageId, request.sagaId(), request.orderId(), request.productId(),
                StockStatus.FAILED, null, null, List.of(failureCode), Instant.now());
        return save(request, response, StockStatus.FAILED, messageId);
    }

    private OrderOutbox save(StockRequest request, StockResponse response,
                             StockStatus stockStatus, UUID messageId) {
        try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_WRITE, request.sagaId())) {
            return orderOutboxRepository.save(OrderOutbox.builder()
                    .id(messageId)
                    .sagaId(request.sagaId())
                    .orderId(request.orderId())
                    .type(SagaTypes.ORDER_PROCESSING)
                    .payload(sagaPayloadCodec.serialize(response))
                    .stockStatus(stockStatus)
                    .requestStatus(request.stockOrderStatus())
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

    public StockResponse toResponse(OrderOutbox outbox) {
        return sagaPayloadCodec.deserialize(outbox.getPayload(), StockResponse.class);
    }
}
