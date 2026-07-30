package org.coupon.couponservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.CouponStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.couponservice.domain.outbox.OrderOutbox;
import org.coupon.couponservice.repository.OrderOutboxRepository;
import org.coupon.couponservice.saga.SagaTypes;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 쿠폰 처리 결과 Outbox의 적재·조회.
 *
 * <p>트랜잭션 경계를 두지 않는다 — 호출부({@code CouponSagaService})의 {@code @Transactional}
 * 안에서 동작해야 <b>쿠폰 변경과 응답 기록이 하나의 로컬 트랜잭션</b>이 된다.
 * 이 둘이 갈라지면 "쿠폰은 소진됐는데 응답은 없는" 상태가 생기고, 그게 사가가 멈추는 방식이다.
 */
@Component
@RequiredArgsConstructor
public class OrderOutboxHelper {

    private final OrderOutboxRepository orderOutboxRepository;
    private final SagaPayloadCodec sagaPayloadCodec;

    /**
     * 이 요청을 이미 처리했는지 확인한다.
     *
     * <p>있으면 도메인 로직을 다시 실행하지 않는다. 쿠폰을 두 번 소진하지 않기 위해서이기도 하지만,
     * 더 중요한 것은 <b>그 결과를 다시 보내야 한다</b>는 점이다 — 중복 요청이 왔다는 건
     * 오케스트레이터가 앞선 응답을 받지 못했다는 뜻이다.
     */
    public Optional<OrderOutbox> findProcessed(UUID sagaId, CouponOrderStatus requestStatus) {
        return orderOutboxRepository.findByTypeAndSagaIdAndRequestStatus(
                SagaTypes.ORDER_PROCESSING, sagaId, requestStatus);
    }

    /**
     * 쿠폰 적용 성공 응답을 적재한다.
     *
     * <p>{@code discountAmount}와 {@code finalAmount}를 함께 싣는다. 주문이 두 값을 그대로
     * 기록하므로 서로 어긋나면 {@code total - discount != final}인 주문 이력이 남는데,
     * 그 시점에는 아무도 예외를 던지지 않는다. 두 값 모두 {@code Coupon}에서 계산해 정합을 보장한다.
     *
     * @param issuedCouponId 실제로 사용한 발급 row. 요청은 {@code couponId}만 실어 오므로
     *                       무엇이 소진됐는지 남기려면 해석 결과를 응답에 담아야 한다
     */
    public OrderOutbox saveApplied(CouponRequest request, Long issuedCouponId,
                                   Long discountAmount, Long finalAmount) {
        UUID messageId = UUID.randomUUID();
        CouponResponse response = new CouponResponse(
                messageId, request.sagaId(), request.orderId(), request.couponId(), issuedCouponId,
                CouponStatus.APPLIED, discountAmount, finalAmount, List.of(), Instant.now());
        return save(request, response, CouponStatus.APPLIED, messageId);
    }

    /** 쿠폰 복구 성공 응답. 되돌린 뒤에는 금액이 필요 없다. */
    public OrderOutbox saveRestored(CouponRequest request, Long issuedCouponId) {
        UUID messageId = UUID.randomUUID();
        CouponResponse response = new CouponResponse(
                messageId, request.sagaId(), request.orderId(), request.couponId(), issuedCouponId,
                CouponStatus.RESTORED, null, null, List.of(), Instant.now());
        return save(request, response, CouponStatus.RESTORED, messageId);
    }

    /**
     * 실패 응답을 적재한다.
     *
     * <p><b>비즈니스 실패는 사가의 정상 결과다.</b> 쿠폰이 이미 사용됐다고 예외를 그대로 던지면
     * 트랜잭션이 롤백되면서 이 응답 row까지 사라지고, 오케스트레이터는 아무 답도 받지 못한 채 멈춘다.
     * 예외를 응답으로 바꾸는 지점이 여기다.
     *
     * <p>{@code issuedCouponId}는 해석에 실패했으면 null이다 — 발급 row를 못 찾은 것이
     * 실패 원인인 경우({@code COUPON_NOT_ISSUED_YET})가 그렇다.
     */
    public OrderOutbox saveFailed(CouponRequest request, Long issuedCouponId, String failureCode) {
        UUID messageId = UUID.randomUUID();
        CouponResponse response = new CouponResponse(
                messageId, request.sagaId(), request.orderId(), request.couponId(), issuedCouponId,
                CouponStatus.FAILED, null, null, List.of(failureCode), Instant.now());
        return save(request, response, CouponStatus.FAILED, messageId);
    }

    private OrderOutbox save(CouponRequest request, CouponResponse response,
                             CouponStatus couponStatus, UUID messageId) {
        return orderOutboxRepository.save(OrderOutbox.builder()
                .id(messageId)
                .sagaId(request.sagaId())
                .orderId(request.orderId())
                .type(SagaTypes.ORDER_PROCESSING)
                .payload(sagaPayloadCodec.serialize(response))
                .couponStatus(couponStatus)
                // 어떤 요청에 대한 응답인지. 정상/보상 응답이 공존할 수 있게 하는 값이다.
                .requestStatus(request.couponOrderStatus())
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
    public CouponResponse toResponse(OrderOutbox outbox) {
        return sagaPayloadCodec.deserialize(outbox.getPayload(), CouponResponse.class);
    }
}
