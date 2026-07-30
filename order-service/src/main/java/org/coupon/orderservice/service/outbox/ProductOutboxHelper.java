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
import org.coupon.orderservice.saga.SagaTypes;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 재고 요청 Outbox의 적재·조회. 트랜잭션 경계는 두지 않는다 —
 * 호출부(사가 스텝, 스케줄러)의 {@code @Transactional} 안에서 동작해야
 * 도메인 변경과 Outbox 저장이 하나의 로컬 트랜잭션으로 묶인다.
 */
@Component
@RequiredArgsConstructor
public class ProductOutboxHelper {

    /** 폴링 대상 사가 상태. COMPENSATING을 빼면 보상이 영원히 발행되지 않는다. */
    private static final List<SagaStatus> PUBLISHABLE =
            List.of(SagaStatus.STARTED, SagaStatus.COMPENSATING);

    private final ProductOutboxRepository productOutboxRepository;
    private final SagaPayloadCodec sagaPayloadCodec;

    /**
     * 재고 요청을 적재한다. 발행하지 않는다 — 커밋 전에 보내면 롤백된 요청이 이미 나가버리므로
     * 여기서 KafkaTemplate을 부르면 그 순간 Outbox 패턴이 무의미해진다. 발행은 스케줄러가 한다.
     *
     * <p>메시지 id를 Outbox의 PK로 그대로 쓴다. payload 안의 {@code id}와 같은 값이라
     * 로그 한 줄로 DB row와 Kafka 메시지를 이어 볼 수 있다.
     */
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

    /**
     * 지금 이 응답을 받아들일 수 있는 Outbox row를 찾는다.
     *
     * <p>비어 있으면 이미 처리됐거나 늦게 도착한 응답이다 — 호출부는 예외 없이 종료해야 한다.
     * {@code requestStatus}를 빼면 정상 응답과 보상 응답을 구분하지 못한다.
     */
    public Optional<ProductOutbox> findAwaiting(UUID sagaId, StockOrderStatus requestStatus, SagaStatus expected) {
        return productOutboxRepository.findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus, List.of(expected));
    }

    /** 상태를 가리지 않고 찾는다. 사가가 끝날 때 이미 전이된 row의 상태를 맞추는 용도다. */
    public Optional<ProductOutbox> find(UUID sagaId, StockOrderStatus requestStatus) {
        return productOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    /**
     * 아직 발행되지 않은 요청 전부.
     *
     * <p>나이 조건을 두지 않는다. 발행 경로가 스케줄러 하나뿐이라 "방금 적재돼 곧 나갈 row"라는
     * 것이 없고, 필터를 두면 모든 주문에 그만큼 지연만 더해진다.
     */
    public List<ProductOutbox> findUnpublished() {
        return productOutboxRepository.findByTypeAndOutboxStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING, OutboxStatus.STARTED, PUBLISHABLE);
    }

    /** 종료된 사가의 row. Cleaner가 지운다. */
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

    /** 발행 직전에 payload를 요청 DTO로 되돌린다. */
    public StockRequest toRequest(ProductOutbox outbox) {
        return sagaPayloadCodec.deserialize(outbox.getPayload(), StockRequest.class);
    }
}
