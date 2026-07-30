package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.coupon.orderservice.repository.CouponOutboxRepository;
import org.coupon.orderservice.saga.SagaTypes;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 쿠폰 요청 Outbox의 적재·조회. 계약과 트랜잭션 규칙은 {@link ProductOutboxHelper}와 같다.
 */
@Component
@RequiredArgsConstructor
public class CouponOutboxHelper {

    private static final List<SagaStatus> PUBLISHABLE =
            List.of(SagaStatus.STARTED, SagaStatus.COMPENSATING);

    private final CouponOutboxRepository couponOutboxRepository;
    private final SagaPayloadCodec sagaPayloadCodec;

    /**
     * 쿠폰 요청을 적재한다.
     *
     * <p>{@code userId}는 주문에 이미 서버 측 값으로 박혀 있는 것을 그대로 쓴다 —
     * 주문 생성 때 인증 principal에서 받았고 클라이언트가 손댈 수 없는 값이다.
     *
     * <p>{@code orderAmount}로 주문 총액을 함께 보낸다. coupon-service가 할인율을 적용할 기준액인데,
     * 이걸 안 실어 보내면 쿠폰 서비스가 주문 스키마를 조회해야 하고 서비스 경계가 무너진다.
     */
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

    public Optional<CouponOutbox> findAwaiting(UUID sagaId, CouponOrderStatus requestStatus, SagaStatus expected) {
        return couponOutboxRepository.findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus, List.of(expected));
    }

    /** 상태를 가리지 않고 찾는다. 사가가 끝날 때 이미 전이된 row의 상태를 맞추는 용도다. */
    public Optional<CouponOutbox> find(UUID sagaId, CouponOrderStatus requestStatus) {
        return couponOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    /** 아직 발행되지 않은 요청 전부. 근거는 {@link ProductOutboxHelper#findUnpublished()} 참조. */
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
