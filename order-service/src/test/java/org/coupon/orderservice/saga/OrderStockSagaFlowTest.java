package org.coupon.orderservice.saga;

import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.RequestType;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainer.class)
class OrderStockSagaFlowTest {

    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final int QUANTITY = 2;
    private static final long UNIT_PRICE = 10_000L;

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

    @Test
    @DisplayName("쿠폰 없는 주문: 생성 → 재고 요청 outbox → RESERVED 응답 → 주문·사가 COMPLETED, 재응답은 무시")
    void stock_only_order_completes() {
        Long orderId = orderService.create(USER_ID, new OrderCreateRequest(PRODUCT_ID, QUANTITY, null)).orderId();

        SagaState state = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        assertThat(state.getType()).isEqualTo(StockOnlyOrderSaga.TYPE);
        assertThat(state.getStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(state.getCurrentStep()).isEqualTo(AbstractOrderSaga.STOCK_RESERVATION);
        assertThat(state.getStepStatus())
                .containsExactly(Map.entry(AbstractOrderSaga.STOCK_RESERVATION, SagaStepStatus.STARTED));

        OutboxEvent request = outboxEventRepository.findAll().stream().findFirst().orElseThrow();
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(request.getAggregateType()).isEqualTo("stock-reservation");
        assertThat(request.getAggregateId()).isEqualTo(state.getId().toString());
        assertThat(request.getType()).isEqualTo(RequestType.REQUEST.name());
        StockReservationRequestPayload payload =
                codec.deserialize(request.getPayload(), StockReservationRequestPayload.class);
        assertThat(payload).isEqualTo(new StockReservationRequestPayload(orderId, PRODUCT_ID, QUANTITY, RequestType.REQUEST));

        String eventId = UUID.randomUUID().toString();
        String body = codec.serialize(new StockReservationResponsePayload(
                orderId, "무선 이어폰", UNIT_PRICE, StockResult.RESERVED, null));
        sagaResponseHandler.onStockResponse(state.getId().toString(), eventId, body);

        Order completed = reload(orderId);
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completed.getTotalAmount()).isEqualTo(UNIT_PRICE * QUANTITY);
        assertThat(completed.getDiscountAmount()).isZero();
        assertThat(completed.getFinalAmount()).isEqualTo(UNIT_PRICE * QUANTITY);
        assertThat(completed.primaryItem().getProductName()).isEqualTo("무선 이어폰");

        SagaState done = sagaStateRepository.findById(state.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(done.getCurrentStep()).isNull();
        assertThat(done.getStepStatus())
                .containsExactly(Map.entry(AbstractOrderSaga.STOCK_RESERVATION, SagaStepStatus.SUCCEEDED));
        assertThat(codec.deserialize(done.getPayload(), OrderSagaPayload.class).orderAmount())
                .isEqualTo(UNIT_PRICE * QUANTITY);
        assertThat(consumedMessageRepository.existsById(eventId)).isTrue();
        assertThat(outboxEventRepository.count()).as("한 스텝 사가는 요청 1건으로 끝난다").isEqualTo(1);

        sagaResponseHandler.onStockResponse(state.getId().toString(), eventId, body);
        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(sagaStateRepository.findById(state.getId()).orElseThrow().getVersion())
                .isEqualTo(done.getVersion());
    }

    @Test
    @DisplayName("재고 부족: 주문 FAILED + 사가 ABORTED, 보상 요청 없음")
    void out_of_stock_fails_order() {
        Long orderId = orderService.create(USER_ID, new OrderCreateRequest(PRODUCT_ID, QUANTITY, null)).orderId();
        SagaState state = sagaStateRepository.findByOrderId(orderId).orElseThrow();

        sagaResponseHandler.onStockResponse(state.getId().toString(), UUID.randomUUID().toString(),
                codec.serialize(new StockReservationResponsePayload(
                        orderId, null, null, StockResult.OUT_OF_STOCK, "PRODUCT_OUT_OF_STOCK")));

        Order failed = reload(orderId);
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("PRODUCT_OUT_OF_STOCK");
        assertThat(sagaStateRepository.findById(state.getId()).orElseThrow().getStatus()).isEqualTo(SagaStatus.ABORTED);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }
}
