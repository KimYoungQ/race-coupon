package org.coupon.orderservice.messaging;

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
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.saga.OrderPlacementSaga;
import org.coupon.orderservice.saga.OrderSagaPayload;
import org.coupon.orderservice.saga.StockOnlyOrderSaga;
import org.coupon.orderservice.saga.framework.SagaContext;
import org.coupon.orderservice.saga.framework.SagaManager;
import org.coupon.orderservice.saga.framework.SagaState;
import org.coupon.orderservice.saga.framework.SagaStepEvent;
import org.coupon.sagapersistence.idempotency.MessageLog;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.coupon.orderservice.saga.AbstractOrderSaga.COUPON_APPLY;
import static org.coupon.orderservice.saga.AbstractOrderSaga.STOCK_RESERVATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaResponseHandlerTest {

    private static final long ORDER_ID = 100L;
    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final int QUANTITY = 2;
    private static final long UNIT_PRICE = 10_000L;
    private static final String EVENT_ID = UUID.randomUUID().toString();

    @Mock
    private SagaManager sagaManager;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MessageLog messageLog;

    private final SagaPayloadCodec codec = new SagaPayloadCodec();
    private final List<SagaStepEvent> published = new ArrayList<>();

    private SagaResponseHandler handler;
    private Order order;
    private SagaState state;
    private StockOnlyOrderSaga saga;

    @BeforeEach
    void setUp() {
        handler = new SagaResponseHandler(sagaManager, orderRepository, messageLog, codec);
        order = Order.builder().userId(USER_ID).couponId(null).productId(PRODUCT_ID).quantity(QUANTITY).build();
        state = SagaState.start(ORDER_ID, StockOnlyOrderSaga.TYPE, codec.serialize(
                new OrderSagaPayload(ORDER_ID, USER_ID, PRODUCT_ID, QUANTITY, null, null)));
        saga = spy(new StockOnlyOrderSaga(state, new SagaContext(e -> published.add((SagaStepEvent) e), codec)));
        saga.start();
    }

    private void sagaIsFound() {
        doReturn(Optional.of(saga)).when(sagaManager).find(eq(state.getId()), any());
    }

    private String response(StockResult result, String failureCode) {
        boolean reserved = result == StockResult.RESERVED;
        return codec.serialize(new StockReservationResponsePayload(
                ORDER_ID, reserved ? "무선 이어폰" : null, reserved ? UNIT_PRICE : null, result, failureCode));
    }

    @Test
    @DisplayName("RESERVED: 단가 스냅샷 → 사가 COMPLETED → 주문 COMPLETED(할인 0), payload 갱신이 스텝 결과보다 먼저")
    void reserved_completes_stock_only_order() {
        sagaIsFound();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        handler.onStockResponse(state.getId().toString(), EVENT_ID, response(StockResult.RESERVED, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getTotalAmount()).isEqualTo(UNIT_PRICE * QUANTITY);
        assertThat(order.getDiscountAmount()).isZero();
        assertThat(order.getFinalAmount()).isEqualTo(UNIT_PRICE * QUANTITY);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getStepStatus()).containsExactly(Map.entry(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED));
        assertThat(saga.payload().orderAmount()).isEqualTo(UNIT_PRICE * QUANTITY);

        InOrder inOrder = inOrder(saga, messageLog);
        inOrder.verify(saga).fixOrderAmount(UNIT_PRICE * QUANTITY);
        inOrder.verify(saga).onStepResult(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED);
        inOrder.verify(messageLog).markProcessed(EVENT_ID);
        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("OUT_OF_STOCK: 첫 스텝 실패라 곧장 ABORTED → 주문 FAILED(failureCode 보존), 보상 요청 없음")
    void out_of_stock_fails_order_without_compensation() {
        sagaIsFound();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        handler.onStockResponse(state.getId().toString(), EVENT_ID,
                response(StockResult.OUT_OF_STOCK, "PRODUCT_OUT_OF_STOCK"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureCode()).isEqualTo("PRODUCT_OUT_OF_STOCK");
        assertThat(order.getTotalAmount()).isZero();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.ABORTED);
        assertThat(saga.getStepStatus()).containsExactly(Map.entry(STOCK_RESERVATION, SagaStepStatus.FAILED));
        verify(saga, never()).fixOrderAmount(anyLong());
        verify(messageLog).markProcessed(EVENT_ID);
        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("이미 처리한 eventId 는 사가도 주문도 건드리지 않는다")
    void already_processed_event_is_ignored() {
        when(messageLog.alreadyProcessed(EVENT_ID)).thenReturn(true);

        handler.onStockResponse(state.getId().toString(), EVENT_ID, response(StockResult.RESERVED, null));

        verify(sagaManager, never()).find(any(), any());
        verify(orderRepository, never()).findById(any());
        verify(messageLog, never()).markProcessed(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("기다리지 않는 스텝의 지연 응답은 상태를 바꾸지 않고 처리 표시만 남긴다")
    void late_response_after_completion_is_marked_but_ignored() {
        saga.onStepResult(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        sagaIsFound();

        handler.onStockResponse(state.getId().toString(), EVENT_ID,
                response(StockResult.OUT_OF_STOCK, "LATE"));

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getStepStatus()).containsExactly(Map.entry(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, never()).findById(any());
        verify(messageLog).markProcessed(EVENT_ID);
    }

    @Nested
    @DisplayName("쿠폰 주문 (order-placement)")
    class CouponOrder {

        private static final long COUPON_ID = 9L;
        private static final long TOTAL = UNIT_PRICE * QUANTITY;
        private static final long DISCOUNT = 2_000L;

        private Order couponOrder;
        private SagaState placementState;
        private OrderPlacementSaga placement;

        @BeforeEach
        void setUpCouponOrder() {
            published.clear();
            couponOrder = Order.builder().userId(USER_ID).couponId(COUPON_ID).productId(PRODUCT_ID).quantity(QUANTITY).build();
            placementState = SagaState.start(ORDER_ID, OrderPlacementSaga.TYPE, codec.serialize(
                    new OrderSagaPayload(ORDER_ID, USER_ID, PRODUCT_ID, QUANTITY, COUPON_ID, null)));
            placement = spy(new OrderPlacementSaga(placementState,
                    new SagaContext(e -> published.add((SagaStepEvent) e), codec)));
            placement.start();
        }

        private void placementIsFound() {
            doReturn(Optional.of(placement)).when(sagaManager).find(eq(placementState.getId()), any());
        }

        private String couponResponse(CouponResult result, String failureCode) {
            boolean applied = result == CouponResult.APPLIED;
            return codec.serialize(new CouponApplyResponsePayload(
                    ORDER_ID, applied ? DISCOUNT : null, applied ? TOTAL - DISCOUNT : null, result, failureCode));
        }

        private void stockReserved() {
            handler.onStockResponse(placementState.getId().toString(), UUID.randomUUID().toString(),
                    response(StockResult.RESERVED, null));
        }

        @Test
        @DisplayName("재고 RESERVED: 주문은 STOCK_RESERVED 에 머물고 쿠폰 요청이 총액을 싣고 나간다")
        void reserved_starts_coupon_step() {
            placementIsFound();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(couponOrder));

            stockReserved();

            assertThat(couponOrder.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
            assertThat(placement.getStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(placement.getCurrentStep()).isEqualTo(COUPON_APPLY);
            assertThat(placement.getStepStatus()).containsOnly(
                    Map.entry(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED),
                    Map.entry(COUPON_APPLY, SagaStepStatus.STARTED));
            assertThat(published).hasSize(2);
            SagaStepEvent couponRequest = published.get(1);
            assertThat(couponRequest.aggregateType()).isEqualTo(COUPON_APPLY);
            assertThat(couponRequest.aggregateId()).isEqualTo(placementState.getId().toString());
            assertThat(couponRequest.type()).isEqualTo(RequestType.REQUEST.name());
            assertThat(couponRequest.payload()).isEqualTo(
                    new CouponApplyRequestPayload(ORDER_ID, USER_ID, COUPON_ID, TOTAL, RequestType.REQUEST));

            InOrder inOrder = inOrder(placement);
            inOrder.verify(placement).fixOrderAmount(TOTAL);
            inOrder.verify(placement).onStepResult(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED);
        }

        @Test
        @DisplayName("쿠폰 APPLIED: 사가 COMPLETED → 주문 COMPLETED(할인·최종 = 응답 값)")
        void applied_completes_order_with_discount() {
            placementIsFound();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(couponOrder));
            stockReserved();

            handler.onCouponResponse(placementState.getId().toString(), EVENT_ID,
                    couponResponse(CouponResult.APPLIED, null));

            assertThat(couponOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(couponOrder.getDiscountAmount()).isEqualTo(DISCOUNT);
            assertThat(couponOrder.getFinalAmount()).isEqualTo(TOTAL - DISCOUNT);
            assertThat(placement.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(placement.getStepStatus()).containsOnly(
                    Map.entry(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED),
                    Map.entry(COUPON_APPLY, SagaStepStatus.SUCCEEDED));
            assertThat(published).as("완료 시 추가 요청 없음").hasSize(2);
            verify(messageLog).markProcessed(EVENT_ID);
        }

        @Test
        @DisplayName("쿠폰 REJECTED: 사가 ABORTING → 주문 COMPENSATING(failureCode) + 재고 CANCEL 발행")
        void rejected_starts_compensation() {
            placementIsFound();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(couponOrder));
            stockReserved();

            handler.onCouponResponse(placementState.getId().toString(), EVENT_ID,
                    couponResponse(CouponResult.REJECTED, "COUPON_MIN_ORDER_AMOUNT_NOT_MET"));

            assertThat(couponOrder.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
            assertThat(couponOrder.getFailureCode()).isEqualTo("COUPON_MIN_ORDER_AMOUNT_NOT_MET");
            assertThat(couponOrder.getDiscountAmount()).isZero();
            assertThat(placement.getStatus()).isEqualTo(SagaStatus.ABORTING);
            assertThat(placement.getCurrentStep()).isEqualTo(STOCK_RESERVATION);
            assertThat(placement.getStepStatus()).containsOnly(
                    Map.entry(STOCK_RESERVATION, SagaStepStatus.COMPENSATING),
                    Map.entry(COUPON_APPLY, SagaStepStatus.FAILED));
            assertThat(published).hasSize(3);
            SagaStepEvent cancel = published.get(2);
            assertThat(cancel.aggregateType()).isEqualTo(STOCK_RESERVATION);
            assertThat(cancel.type()).isEqualTo(RequestType.CANCEL.name());
            assertThat(cancel.payload()).isEqualTo(
                    new StockReservationRequestPayload(ORDER_ID, PRODUCT_ID, QUANTITY, RequestType.CANCEL));
        }

        @Test
        @DisplayName("보상 뒤 재고 RELEASED: 사가 ABORTED → 주문 FAILED, 원래 failureCode 유지")
        void released_after_rejection_completes_compensation() {
            placementIsFound();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(couponOrder));
            stockReserved();
            handler.onCouponResponse(placementState.getId().toString(), UUID.randomUUID().toString(),
                    couponResponse(CouponResult.REJECTED, "COUPON_MIN_ORDER_AMOUNT_NOT_MET"));

            handler.onStockResponse(placementState.getId().toString(), EVENT_ID,
                    response(StockResult.RELEASED, null));

            assertThat(couponOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(couponOrder.getFailureCode()).isEqualTo("COUPON_MIN_ORDER_AMOUNT_NOT_MET");
            assertThat(placement.getStatus()).isEqualTo(SagaStatus.ABORTED);
            assertThat(placement.getCurrentStep()).isNull();
            assertThat(placement.getStepStatus()).containsOnly(
                    Map.entry(STOCK_RESERVATION, SagaStepStatus.COMPENSATED),
                    Map.entry(COUPON_APPLY, SagaStepStatus.FAILED));
            assertThat(published).as("보상 완료 뒤 추가 발행 없음").hasSize(3);
            verify(placement, times(1)).fixOrderAmount(TOTAL);
        }

        @Test
        @DisplayName("쿠폰 스텝을 기다리지 않을 때 온 쿠폰 응답은 무시하고 처리 표시만 남긴다")
        void coupon_response_before_stock_is_ignored() {
            placementIsFound();

            handler.onCouponResponse(placementState.getId().toString(), EVENT_ID,
                    couponResponse(CouponResult.APPLIED, null));

            assertThat(placement.getStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(placement.getStepStatus()).containsExactly(Map.entry(STOCK_RESERVATION, SagaStepStatus.STARTED));
            assertThat(couponOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
            verify(orderRepository, never()).findById(any());
            verify(messageLog).markProcessed(EVENT_ID);
        }

        @Test
        @DisplayName("재고 응답 전에 쿠폰 요청을 만들면 즉시 예외다 — 순서 위반이 0원 요청으로 새지 않는다")
        void coupon_request_without_order_amount_fails_fast() {
            assertThatThrownBy(() -> placement.onStepResult(STOCK_RESERVATION, SagaStepStatus.SUCCEEDED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고 응답 전에는");
        }
    }
}
