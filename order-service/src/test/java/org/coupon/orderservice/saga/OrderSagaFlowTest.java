package org.coupon.orderservice.saga;

import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.event.RequestType;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.common.saga.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderStatus;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.messaging.SagaResponseHandler;
import org.coupon.orderservice.repository.OrderItemRepository;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.saga.framework.SagaState;
import org.coupon.orderservice.saga.framework.SagaStateRepository;
import org.coupon.orderservice.service.OrderService;
import org.coupon.orderservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.idempotency.ConsumedMessageRepository;
import org.coupon.sagapersistence.outbox.OutboxEvent;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.coupon.orderservice.saga.AbstractOrderSaga.COUPON_APPLY;
import static org.coupon.orderservice.saga.AbstractOrderSaga.STOCK_RESERVATION;

@SpringBootTest
@Import(MySqlTestContainer.class)
class OrderSagaFlowTest {

        private static final long USER_ID = 42L;
        private static final long PRODUCT_ID = 7L;
        private static final long COUPON_ID = 9L;
        private static final int QUANTITY = 2;
        private static final long UNIT_PRICE = 10_000L;
        private static final long TOTAL = UNIT_PRICE * QUANTITY;

        @Autowired
        private OrderService orderService;

        @Autowired
        private SagaResponseHandler sagaResponseHandler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ConsumedMessageRepository consumedMessageRepository;

    @Autowired
    private SagaPayloadCodec codec;

    @AfterEach
    void cleanUp() {
        consumedMessageRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        sagaStateRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("재고 예약과 쿠폰 적용이 모두 성공하면 주문과 사가는 COMPLETED 상태가 된다")
    void allStepsSucceed() {
        // given
        Long orderId = createOrder(COUPON_ID);
        UUID sagaId = sagaIdOf(orderId);
        stockResponse(sagaId, StockResult.RESERVED, null);

        // when
        couponResponse(sagaId, CouponResult.APPLIED, null);

        // then
        Order order = findOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getFinalAmount()).isEqualTo(TOTAL - 2_000L);
        assertThat(findSaga(sagaId).getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(outboxEventRepository.count()).as("재고 요청 1건 + 쿠폰 요청 1건").isEqualTo(2);
    }

    @Test
    @DisplayName("쿠폰이 거절되면 이미 예약한 재고를 되돌리는 CANCEL 요청이 발행되고 주문은 FAILED로 끝난다")
    void couponRejectedCompensatesStock() {
        // given
        Long orderId = createOrder(COUPON_ID);
        UUID sagaId = sagaIdOf(orderId);
        stockResponse(sagaId, StockResult.RESERVED, null);

        // when
        couponResponse(sagaId, CouponResult.REJECTED, "COUPON_ALREADY_USED");
        stockResponse(sagaId, StockResult.RELEASED, null);

        // then
        List<OutboxEvent> stockCancels = outboxOf(STOCK_RESERVATION, RequestType.CANCEL);
        assertThat(stockCancels).hasSize(1);
        Order order = findOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureCode()).isEqualTo("COUPON_ALREADY_USED");
        assertThat(findSaga(sagaId).getStatus()).isEqualTo(SagaStatus.ABORTED);
    }

    @Test
    @DisplayName("첫 단계인 재고 예약이 실패하면 다음 단계인 쿠폰 요청은 나가지 않고 주문은 FAILED가 된다")
    void firstStepFailsStopsSaga() {
        // given
        Long orderId = createOrder(COUPON_ID);
        UUID sagaId = sagaIdOf(orderId);

        // when
        stockResponse(sagaId, StockResult.OUT_OF_STOCK, "PRODUCT_OUT_OF_STOCK");

        // then
        assertThat(outboxOf(COUPON_APPLY, RequestType.REQUEST)).isEmpty();
        assertThat(outboxOf(STOCK_RESERVATION, RequestType.CANCEL)).as("되돌릴 것이 없으니 보상도 없다").isEmpty();
        assertThat(findOrder(orderId).getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(findSaga(sagaId).getStatus()).isEqualTo(SagaStatus.ABORTED);
    }

    @Test
    @DisplayName("같은 eventId의 응답을 다시 받아도 주문 상태와 outbox 건수는 그대로다")
    void duplicateResponseIsIgnored() {
        // given
        Long orderId = createOrder(null);
        UUID sagaId = sagaIdOf(orderId);
        String eventId = UUID.randomUUID().toString();
        String body = codec.serialize(new StockReservationResponsePayload(
                orderId, "무선 이어폰", UNIT_PRICE, StockResult.RESERVED, null));
        sagaResponseHandler.onStockResponse(sagaId.toString(), eventId, body);
        long outboxCountBefore = outboxEventRepository.count();

        // when
        sagaResponseHandler.onStockResponse(sagaId.toString(), eventId, body);

        // then
        assertThat(findOrder(orderId).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(findSaga(sagaId).getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxCountBefore);
    }

    private Long createOrder(Long couponId) {
        return orderService.create(USER_ID, new OrderCreateRequest(PRODUCT_ID, QUANTITY, couponId)).orderId();
    }

    private UUID sagaIdOf(Long orderId) {
        return sagaStateRepository.findByOrderId(orderId).orElseThrow().getId();
    }

    private void stockResponse(UUID sagaId, StockResult result, String failureCode) {
        boolean reserved = result == StockResult.RESERVED;
        sagaResponseHandler.onStockResponse(sagaId.toString(), UUID.randomUUID().toString(),
                codec.serialize(new StockReservationResponsePayload(
                        findSaga(sagaId).getOrderId(),
                        reserved ? "무선 이어폰" : null,
                        reserved ? UNIT_PRICE : null,
                        result, failureCode)));
    }

    private void couponResponse(UUID sagaId, CouponResult result, String failureCode) {
        boolean applied = result == CouponResult.APPLIED;
        sagaResponseHandler.onCouponResponse(sagaId.toString(), UUID.randomUUID().toString(),
                codec.serialize(new CouponApplyResponsePayload(
                        findSaga(sagaId).getOrderId(),
                        applied ? 2_000L : null,
                        applied ? TOTAL - 2_000L : null,
                        result, failureCode)));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private SagaState findSaga(UUID sagaId) {
        return sagaStateRepository.findById(sagaId).orElseThrow();
    }

    private List<OutboxEvent> outboxOf(String aggregateType, RequestType type) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateType().equals(aggregateType) && e.getType().equals(type.name()))
                .toList();
    }
}
