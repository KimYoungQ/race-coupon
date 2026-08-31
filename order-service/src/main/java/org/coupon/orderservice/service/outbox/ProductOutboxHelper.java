package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockRequest;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderItem;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.coupon.orderservice.repository.ProductOutboxRepository;
import org.coupon.orderservice.saga.SagaTraceTag;
import org.coupon.orderservice.saga.SagaTypes;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductOutboxHelper {

    private static final List<SagaStatus> PUBLISHABLE =
            List.of(SagaStatus.STARTED, SagaStatus.COMPENSATING);

    private final ProductOutboxRepository productOutboxRepository;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final SagaTraceTag sagaTraceTag;

    public ProductOutbox saveProductOutboxMessage(Order order, StockOrderStatus requestStatus,
                                                 SagaStatus sagaStatus) {
        UUID messageId = UUID.randomUUID();
        OrderItem item = order.primaryItem();

        StockRequest request = new StockRequest(
                messageId,
                order.getSagaId(),
                order.getId(),
                item.getProductId(),
                item.getQuantity(),
                requestStatus,
                Instant.now());

        try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_WRITE, order.getSagaId())) {
            return productOutboxRepository.save(ProductOutbox.builder()
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

    public Optional<ProductOutbox> findAwaiting(UUID sagaId, StockOrderStatus requestStatus, SagaStatus expected) {
        return productOutboxRepository.findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus, List.of(expected));
    }

    public Optional<ProductOutbox> find(UUID sagaId, StockOrderStatus requestStatus) {
        return productOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    public List<ProductOutbox> findUnpublished() {
        return productOutboxRepository.findByTypeAndOutboxStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING, OutboxStatus.STARTED, PUBLISHABLE);
    }

    public List<ProductOutbox> findTerminal() {
        return productOutboxRepository.findByTypeAndOutboxStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING,
                OutboxStatus.COMPLETED,
                List.of(SagaStatus.SUCCEEDED, SagaStatus.COMPENSATED, SagaStatus.FAILED));
    }

    public void delete(List<ProductOutbox> outboxes) {
        productOutboxRepository.deleteAllInBatch(outboxes);
    }

    public Optional<ProductOutbox> findById(UUID outboxId) {
        return productOutboxRepository.findById(outboxId);
    }

    public StockRequest toRequest(ProductOutbox outbox) {
        return sagaPayloadCodec.deserialize(outbox.getPayload(), StockRequest.class);
    }
}
