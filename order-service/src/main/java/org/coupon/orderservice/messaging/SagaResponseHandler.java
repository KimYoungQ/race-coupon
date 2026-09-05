package org.coupon.orderservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderStatus;
import org.coupon.orderservice.exception.OrderNotFoundException;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.saga.AbstractOrderSaga;
import org.coupon.orderservice.saga.OrderPlacementSaga;
import org.coupon.orderservice.saga.StockOnlyOrderSaga;
import org.coupon.orderservice.saga.framework.SagaFactory;
import org.coupon.orderservice.saga.framework.SagaManager;
import org.coupon.sagapersistence.idempotency.MessageLog;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.coupon.orderservice.saga.AbstractOrderSaga.COUPON_APPLY;
import static org.coupon.orderservice.saga.AbstractOrderSaga.STOCK_RESERVATION;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaResponseHandler {

    static final String UNKNOWN_FAILURE = "UNKNOWN_FAILURE";

    private final SagaManager sagaManager;
    private final OrderRepository orderRepository;
    private final MessageLog messageLog;
    private final SagaPayloadCodec sagaPayloadCodec;

    @Transactional
    public void onStockResponse(String sagaId, String eventId, String body) {
        if (messageLog.alreadyProcessed(eventId)) {
            log.info("이미 처리한 재고 응답: sagaId={}, eventId={}", sagaId, eventId);
            return;
        }
        StockReservationResponsePayload response =
                sagaPayloadCodec.deserialize(body, StockReservationResponsePayload.class);
        AbstractOrderSaga saga = findSaga(UUID.fromString(sagaId));

        if (!saga.isAwaiting(STOCK_RESERVATION)) {
            log.info("지연/무효 재고 응답 무시: sagaId={}, sagaStatus={}, stepStatus={}, result={}",
                    sagaId, saga.getStatus(), saga.getStepStatus(), response.result());
            messageLog.markProcessed(eventId);
            return;
        }

        Order order = findOrder(saga.getOrderId());
        if (response.result() == StockResult.RESERVED) {
            order.reserveStock(response.productName(), response.unitPrice());
            saga.fixOrderAmount(order.getTotalAmount());
        }
        saga.onStepResult(STOCK_RESERVATION, response.result().toStepStatus());
        syncOrderStatus(saga, order, response.failureCode(), 0L, order.getTotalAmount());
        messageLog.markProcessed(eventId);

        log.info("재고 응답 처리: sagaId={}, orderId={}, result={}, sagaStatus={}, orderStatus={}",
                sagaId, order.getId(), response.result(), saga.getStatus(), order.getStatus());
    }

    @Transactional
    public void onCouponResponse(String sagaId, String eventId, String body) {
        if (messageLog.alreadyProcessed(eventId)) {
            log.info("이미 처리한 쿠폰 응답: sagaId={}, eventId={}", sagaId, eventId);
            return;
        }
        CouponApplyResponsePayload response =
                sagaPayloadCodec.deserialize(body, CouponApplyResponsePayload.class);
        AbstractOrderSaga saga = findSaga(UUID.fromString(sagaId));

        if (!saga.isAwaiting(COUPON_APPLY)) {
            log.info("지연/무효 쿠폰 응답 무시: sagaId={}, sagaStatus={}, stepStatus={}, result={}",
                    sagaId, saga.getStatus(), saga.getStepStatus(), response.result());
            messageLog.markProcessed(eventId);
            return;
        }

        Order order = findOrder(saga.getOrderId());
        saga.onStepResult(COUPON_APPLY, response.result().toStepStatus());
        boolean applied = response.result() == CouponResult.APPLIED;
        syncOrderStatus(saga, order, response.failureCode(),
                applied ? response.discountAmount() : 0L,
                applied ? response.finalAmount() : order.getTotalAmount());
        messageLog.markProcessed(eventId);

        log.info("쿠폰 응답 처리: sagaId={}, orderId={}, result={}, sagaStatus={}, orderStatus={}, 할인={}, 최종={}",
                sagaId, order.getId(), response.result(), saga.getStatus(), order.getStatus(),
                order.getDiscountAmount(), order.getFinalAmount());
    }

    private void syncOrderStatus(AbstractOrderSaga saga, Order order, String failureCode,
                                 long discountAmount, long finalAmount) {
        switch (saga.getStatus()) {
            case COMPLETED -> order.complete(discountAmount, finalAmount);
            case ABORTING -> order.startCompensation(failureCodeOf(failureCode));
            case ABORTED -> {
                if (order.getStatus() == OrderStatus.COMPENSATING) {
                    order.completeCompensation();
                } else {
                    order.fail(failureCodeOf(failureCode));
                }
            }
            case STARTED -> {
            }
        }
    }

    private AbstractOrderSaga findSaga(UUID sagaId) {
        return sagaManager.<AbstractOrderSaga>find(sagaId, this::factoryFor)
                .orElseThrow(() -> new IllegalStateException("응답에 해당하는 사가가 없다: " + sagaId));
    }

    private SagaFactory<? extends AbstractOrderSaga> factoryFor(String type) {
        return switch (type) {
            case StockOnlyOrderSaga.TYPE -> StockOnlyOrderSaga::new;
            case OrderPlacementSaga.TYPE -> OrderPlacementSaga::new;
            default -> throw new IllegalStateException("알 수 없는 사가 type: " + type);
        };
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private String failureCodeOf(String failureCode) {
        return failureCode == null || failureCode.isBlank() ? UNKNOWN_FAILURE : failureCode;
    }
}
