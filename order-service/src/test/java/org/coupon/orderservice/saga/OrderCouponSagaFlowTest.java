package org.coupon.orderservice.saga;

import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.event.RequestType;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.common.saga.SagaStatus;
import org.coupon.common.saga.SagaStepStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderStatus;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.messaging.SagaResponseHandler;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.saga.framework.SagaState;
import org.coupon.orderservice.saga.framework.SagaStateRepository;
import org.coupon.orderservice.service.OrderService;
import org.coupon.orderservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.idempotency.ConsumedMessageRepository;
import org.coupon.sagapersistence.outbox.OutboxEvent;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.coupon.orderservice.saga.AbstractOrderSaga.COUPON_APPLY;
import static org.coupon.orderservice.saga.AbstractOrderSaga.STOCK_RESERVATION;

@SpringBootTest
@Import(MySqlTestContainer.class)
class OrderCouponSagaFlowTest {

    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final long COUPON_ID = 9L;
    private static final int QUANTITY = 2;
    private static final long UNIT_PRICE = 10_000L;
    private static final long TOTAL = UNIT_PRICE * QUANTITY;
    private static final long DISCOUNT = 2_000L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SagaResponseHandler sagaResponseHandler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ConsumedMessageRepository consumedMessageRepository;

    @Autowired
    private SagaPayloadCodec codec;

    @BeforeEach
    void setUp() {
        consumedMessageRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        sagaStateRepository.deleteAllInBatch();
        orderRepository.deleteAll();
    }

    private Order reload(Long orderId) {
        return orderRepository.findByIdAndUserIdWithItems(orderId, USER_ID).orElseThrow();
    }

    private SagaState state(UUID sagaId) {
        return sagaStateRepository.findById(sagaId).orElseThrow();
    }

    private List<OutboxEvent> outboxOf(String aggregateType, RequestType type) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateType().equals(aggregateType) && e.getType().equals(type.name()))
                .toList();
    }

    private UUID placeAndReserve(Long orderId) {
        SagaState state = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        sagaResponseHandler.onStockResponse(state.getId().toString(), UUID.randomUUID().toString(),
                codec.serialize(new StockReservationResponsePayload(
                        orderId, "무선 이어폰", UNIT_PRICE, StockResult.RESERVED, null)));
        return state.getId();
    }

    @Test
    @DisplayName("쿠폰 주문 ③: 생성 → 재고 RESERVED → 쿠폰 요청(총액) outbox → APPLIED → 주문 COMPLETED + 할인")
    void coupon_order_completes_with_discount() {
        Long orderId = orderService.create(USER_ID, new OrderCreateRequest(PRODUCT_ID, QUANTITY, COUPON_ID)).orderId();

        SagaState created = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        assertThat(created.getType()).isEqualTo(OrderPlacementSaga.TYPE);
        assertThat(created.getCurrentStep()).isEqualTo(STOCK_RESERVATION);
        assertThat(outboxOf(STOCK_RESERVATION, RequestType.REQUEST)).hasSize(1);
        assertThat(outboxOf(COUPON_APPLY, RequestType.REQUEST)).as("쿠폰 요청은 재고 응답 뒤에 나간다").isEmpty();

        UUID sagaId = placeAndReserve(orderId);

        Order reserved = reload(orderId);
        assertThat(reserved.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
        assertThat(reserved.getTotalAmount()).isEqualTo(TOTAL);
        SagaState awaitingCoupon = state(sagaId);
        assertThat(awaitingCoupon.getStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(awaitingCoupon.getCurrentStep()).isEqualTo(COUPON_APPLY);
        assertThat(awaitingCoupon.getStepStatus()).containsOnly(
                Map.entry(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED),
                Map.entry(COUPON_APPLY, SagaStepStatus.STARTED));
        assertThat(codec.deserialize(awaitingCoupon.getPayload(), OrderSagaPayload.class).orderAmount()).isEqualTo(TOTAL);

        List<OutboxEvent> couponRequests = outboxOf(COUPON_APPLY, RequestType.REQUEST);
        assertThat(couponRequests).hasSize(1);
        assertThat(couponRequests.get(0).getAggregateId()).isEqualTo(sagaId.toString());
        assertThat(codec.deserialize(couponRequests.get(0).getPayload(), CouponApplyRequestPayload.class))
                .isEqualTo(new CouponApplyRequestPayload(orderId, USER_ID, COUPON_ID, TOTAL, RequestType.REQUEST));

        String eventId = UUID.randomUUID().toString();
        sagaResponseHandler.onCouponResponse(sagaId.toString(), eventId,
                codec.serialize(new CouponApplyResponsePayload(
                        orderId, DISCOUNT, TOTAL - DISCOUNT, CouponResult.APPLIED, null)));

        Order completed = reload(orderId);
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completed.getDiscountAmount()).isEqualTo(DISCOUNT).isPositive();
        assertThat(completed.getFinalAmount()).isEqualTo(TOTAL - DISCOUNT);
        SagaState done = state(sagaId);
        assertThat(done.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(done.getCurrentStep()).isNull();
        assertThat(done.getStepStatus()).containsOnly(
                Map.entry(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED),
                Map.entry(COUPON_APPLY, SagaStepStatus.SUCCEEDED));
        assertThat(consumedMessageRepository.existsById(eventId)).isTrue();
        assertThat(outboxEventRepository.count()).as("요청 2건(재고, 쿠폰)으로 끝난다").isEqualTo(2);

        sagaResponseHandler.onCouponResponse(sagaId.toString(), eventId,
                codec.serialize(new CouponApplyResponsePayload(
                        orderId, DISCOUNT, TOTAL - DISCOUNT, CouponResult.APPLIED, null)));
        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(state(sagaId).getVersion()).isEqualTo(done.getVersion());
    }

    @Test
    @DisplayName("쿠폰 주문 ④: REJECTED → 주문 COMPENSATING + 사가 ABORTING + 재고 CANCEL outbox → RELEASED → FAILED/ABORTED")
    void coupon_rejection_compensates_stock() {
        Long orderId = orderService.create(USER_ID, new OrderCreateRequest(PRODUCT_ID, QUANTITY, COUPON_ID)).orderId();
        UUID sagaId = placeAndReserve(orderId);

        sagaResponseHandler.onCouponResponse(sagaId.toString(), UUID.randomUUID().toString(),
                codec.serialize(new CouponApplyResponsePayload(
                        orderId, null, null, CouponResult.REJECTED, "COUPON_MIN_ORDER_AMOUNT_NOT_MET")));

        Order compensating = reload(orderId);
        assertThat(compensating.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
        assertThat(compensating.getFailureCode()).isEqualTo("COUPON_MIN_ORDER_AMOUNT_NOT_MET");
        SagaState aborting = state(sagaId);
        assertThat(aborting.getStatus()).isEqualTo(SagaStatus.ABORTING);
        assertThat(aborting.getCurrentStep()).isEqualTo(STOCK_RESERVATION);
        assertThat(aborting.getStepStatus()).containsOnly(
                Map.entry(STOCK_RESERVATION, SagaStepStatus.COMPENSATING),
                Map.entry(COUPON_APPLY, SagaStepStatus.FAILED));

        List<OutboxEvent> cancels = outboxOf(STOCK_RESERVATION, RequestType.CANCEL);
        assertThat(cancels).hasSize(1);
        assertThat(cancels.get(0).getAggregateId()).isEqualTo(sagaId.toString());
        assertThat(codec.deserialize(cancels.get(0).getPayload(), StockReservationRequestPayload.class))
                .isEqualTo(new StockReservationRequestPayload(orderId, PRODUCT_ID, QUANTITY, RequestType.CANCEL));

        sagaResponseHandler.onStockResponse(sagaId.toString(), UUID.randomUUID().toString(),
                codec.serialize(new StockReservationResponsePayload(
                        orderId, null, null, StockResult.RELEASED, null)));

        Order failed = reload(orderId);
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("COUPON_MIN_ORDER_AMOUNT_NOT_MET");
        assertThat(failed.getDiscountAmount()).isZero();
        SagaState aborted = state(sagaId);
        assertThat(aborted.getStatus()).isEqualTo(SagaStatus.ABORTED);
        assertThat(aborted.getCurrentStep()).isNull();
        assertThat(aborted.getStepStatus()).containsOnly(
                Map.entry(STOCK_RESERVATION, SagaStepStatus.COMPENSATED),
                Map.entry(COUPON_APPLY, SagaStepStatus.FAILED));
        assertThat(outboxEventRepository.count()).as("재고 요청, 쿠폰 요청, 재고 취소 = 3건").isEqualTo(3);
    }
}
