package org.coupon.productservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockRequest;
import org.coupon.common.event.StockResponse;
import org.coupon.common.event.StockStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.productservice.domain.outbox.OrderOutbox;
import org.coupon.productservice.repository.OrderOutboxRepository;
import org.coupon.productservice.saga.SagaTypes;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 재고 처리 결과 Outbox의 적재·조회.
 *
 * <p>트랜잭션 경계를 두지 않는다 — 호출부({@code StockSagaService})의 {@code @Transactional}
 * 안에서 동작해야 <b>재고 변경과 응답 기록이 하나의 로컬 트랜잭션</b>이 된다.
 * 이 둘이 갈라지면 "재고는 깎였는데 응답은 없는" 상태가 생기고, 그게 사가가 멈추는 방식이다.
 */
@Component
@RequiredArgsConstructor
public class OrderOutboxHelper {

    private final OrderOutboxRepository orderOutboxRepository;
    private final SagaPayloadCodec sagaPayloadCodec;

    /**
     * 이 요청을 이미 처리했는지 확인한다.
     *
     * <p>있으면 도메인 로직을 다시 실행하지 않는다. 재고를 두 번 깎지 않기 위해서이기도 하지만,
     * 더 중요한 것은 <b>그 결과를 다시 보내야 한다</b>는 점이다 — 중복 요청이 왔다는 건
     * 오케스트레이터가 앞선 응답을 받지 못했다는 뜻이다.
     */
    public Optional<OrderOutbox> findProcessed(UUID sagaId, StockOrderStatus requestStatus) {
        return orderOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    /**
     * 재고 예약 성공 응답을 적재한다.
     *
     * <p>{@code productName}·{@code unitPrice}를 함께 싣는다. 주문 서비스의
     * {@code Order.reserveStock(String, long)}이 이 값들을 받아 품목 스냅샷과 총액을 확정하도록
     * 설계돼 있어서, 빼면 주문 쪽이 상품 테이블을 직접 조회해야 하고 서비스 경계가 무너진다.
     */
    public OrderOutbox saveReserved(StockRequest request, String productName, Long unitPrice) {
        UUID messageId = UUID.randomUUID();
        StockResponse response = new StockResponse(
                messageId, request.sagaId(), request.orderId(), request.productId(),
                StockStatus.RESERVED, productName, unitPrice, List.of(), Instant.now());
        return save(request, response, StockStatus.RESERVED, messageId);
    }

    /** 재고 복구 성공 응답. 되돌린 뒤에는 단가 스냅샷이 필요 없다. */
    public OrderOutbox saveRestored(StockRequest request) {
        UUID messageId = UUID.randomUUID();
        StockResponse response = new StockResponse(
                messageId, request.sagaId(), request.orderId(), request.productId(),
                StockStatus.RESTORED, null, null, List.of(), Instant.now());
        return save(request, response, StockStatus.RESTORED, messageId);
    }

    /**
     * 실패 응답을 적재한다.
     *
     * <p><b>비즈니스 실패는 사가의 정상 결과다.</b> 재고가 부족하다고 예외를 그대로 던지면
     * 트랜잭션이 롤백되면서 이 응답 row까지 사라지고, 오케스트레이터는 아무 답도 받지 못한 채 멈춘다.
     * 예외를 응답으로 바꾸는 지점이 여기다.
     */
    public OrderOutbox saveFailed(StockRequest request, String failureCode) {
        UUID messageId = UUID.randomUUID();
        StockResponse response = new StockResponse(
                messageId, request.sagaId(), request.orderId(), request.productId(),
                StockStatus.FAILED, null, null, List.of(failureCode), Instant.now());
        return save(request, response, StockStatus.FAILED, messageId);
    }

    private OrderOutbox save(StockRequest request, StockResponse response,
                             StockStatus stockStatus, UUID messageId) {
        return orderOutboxRepository.save(OrderOutbox.builder()
                .id(messageId)
                .sagaId(request.sagaId())
                .orderId(request.orderId())
                .type(SagaTypes.ORDER_PROCESSING)
                .payload(sagaPayloadCodec.serialize(response))
                .stockStatus(stockStatus)
                // 어떤 요청에 대한 응답인지. 정상/보상 응답이 공존할 수 있게 하는 값이다.
                .requestStatus(request.stockOrderStatus())
                .build());
    }

    /** 아직 발행되지 않은 응답 전부. */
    public List<OrderOutbox> findUnpublished() {
        return orderOutboxRepository.findByTypeAndOutboxStatus(
                SagaTypes.ORDER_PROCESSING, OutboxStatus.STARTED);
    }

    public Optional<OrderOutbox> findById(UUID outboxId) {
        return orderOutboxRepository.findById(outboxId);
    }

    /** 발행 직전에 payload를 응답 DTO로 되돌린다. */
    public StockResponse toResponse(OrderOutbox outbox) {
        return sagaPayloadCodec.deserialize(outbox.getPayload(), StockResponse.class);
    }
}
