package org.coupon.orderservice.saga;

import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.CouponStatus;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockResponse;
import org.coupon.common.event.StockStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderStatus;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.repository.CouponOutboxRepository;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.repository.ProductOutboxRepository;
import org.coupon.orderservice.service.OrderService;
import org.coupon.orderservice.service.outbox.CouponOutboxHelper;
import org.coupon.orderservice.service.outbox.ProductOutboxHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사가 전 구간. 요청 적재부터 응답 수신, 보상까지 <b>주문이 실제로 끝까지 도는지</b> 본다.
 *
 * <p>Kafka를 거치지 않고 사가 스텝을 직접 호출한다 — 검증 대상이 메시징이 아니라
 * "상태가 순서대로 전이되는가", "중복 응답에 금액이 두 번 반영되지 않는가",
 * "보상이 원래 실패 원인을 지키는가"이기 때문이다.
 */
@SpringBootTest
class OrderSagaFlowTest {

    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final long COUPON_ID = 9L;
    private static final long UNIT_PRICE = 10_000L;
    private static final int QUANTITY = 2;
    private static final long TOTAL = UNIT_PRICE * QUANTITY;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderProductSaga orderProductSaga;

    @Autowired
    private OrderCouponSaga orderCouponSaga;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductOutboxRepository productOutboxRepository;

    @Autowired
    private CouponOutboxRepository couponOutboxRepository;

    @Autowired
    private ProductOutboxHelper productOutboxHelper;

    @Autowired
    private CouponOutboxHelper couponOutboxHelper;

    @BeforeEach
    void setUp() {
        productOutboxRepository.deleteAllInBatch();
        couponOutboxRepository.deleteAllInBatch();
        // deleteAllInBatch는 cascade를 타지 않아 order_item의 FK에 걸린다
        orderRepository.deleteAll();
    }

    /** 주문을 접수하고 사가 상관 키를 돌려준다. */
    private Order placeOrder(Long couponId) {
        Long orderId = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, QUANTITY, couponId)).orderId();
        return orderRepository.findById(orderId).orElseThrow();
    }

    /**
     * 품목까지 함께 읽는다. {@code open-in-view: false}라 트랜잭션 밖에서 LAZY 컬렉션을 건드리면
     * {@code LazyInitializationException}이 난다 — 사가 스텝은 자기 트랜잭션 안에서 접근하므로
     * 무관하지만, 검증하는 쪽은 join fetch로 가져와야 한다.
     */
    private Order reload(Order order) {
        return orderRepository.findByIdAndUserIdWithItems(order.getId(), USER_ID).orElseThrow();
    }

    private StockResponse stockResponse(Order order, StockStatus status, String failureCode) {
        return new StockResponse(UUID.randomUUID(), order.getSagaId(), order.getId(), PRODUCT_ID,
                status,
                status == StockStatus.RESERVED ? "무선 이어폰" : null,
                status == StockStatus.RESERVED ? UNIT_PRICE : null,
                failureCode == null ? List.of() : List.of(failureCode),
                Instant.now());
    }

    private CouponResponse couponResponse(Order order, CouponStatus status, String failureCode) {
        boolean applied = status == CouponStatus.APPLIED;
        return new CouponResponse(UUID.randomUUID(), order.getSagaId(), order.getId(), COUPON_ID,
                555L, status,
                applied ? 2_000L : null,
                applied ? TOTAL - 2_000L : null,
                failureCode == null ? List.of() : List.of(failureCode),
                Instant.now());
    }

    @Nested
    @DisplayName("정상 경로")
    class HappyPath {

        @Test
        @DisplayName("재고 예약 → 쿠폰 적용으로 주문이 완료된다")
        void completes_through_stock_and_coupon() {
            Order order = placeOrder(COUPON_ID);

            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));

            Order afterStock = reload(order);
            assertThat(afterStock.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
            // 응답이 실어온 단가로 총액이 확정된다
            assertThat(afterStock.getTotalAmount()).isEqualTo(TOTAL);
            assertThat(afterStock.primaryItem().getProductName()).isEqualTo("무선 이어폰");
            // 다음 단계가 '기록'됐다 — 아직 발행되지는 않았다
            assertThat(couponOutboxRepository.findAll()).hasSize(1);

            orderCouponSaga.couponApplied(couponResponse(order, CouponStatus.APPLIED, null));

            Order completed = reload(order);
            assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(completed.getDiscountAmount()).isEqualTo(2_000L);
            assertThat(completed.getFinalAmount()).isEqualTo(TOTAL - 2_000L);
        }

        @Test
        @DisplayName("사가가 끝나면 두 Outbox가 모두 SUCCEEDED가 된다 — Cleaner가 지울 수 있어야 한다")
        void both_outboxes_reach_terminal_state() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));
            orderCouponSaga.couponApplied(couponResponse(order, CouponStatus.APPLIED, null));

            assertThat(productOutboxHelper.find(order.getSagaId(), StockOrderStatus.PENDING)
                    .orElseThrow().getSagaStatus())
                    .as("여기서 올려주지 않으면 재고 Outbox가 PROCESSING에 영원히 남는다")
                    .isEqualTo(SagaStatus.SUCCEEDED);
            assertThat(couponOutboxHelper.find(order.getSagaId(), CouponOrderStatus.PENDING)
                    .orElseThrow().getSagaStatus())
                    .isEqualTo(SagaStatus.SUCCEEDED);
        }

        @Test
        @DisplayName("쿠폰 없는 주문은 재고 예약만으로 완료되고 쿠폰 Outbox를 만들지 않는다")
        void order_without_coupon_completes_at_stock_step() {
            Order order = placeOrder(null);

            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));

            Order completed = reload(order);
            assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(completed.getDiscountAmount()).isZero();
            assertThat(completed.getFinalAmount()).isEqualTo(TOTAL);
            assertThat(couponOutboxRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("실패·보상 경로")
    class FailurePath {

        @Test
        @DisplayName("재고 예약 실패는 되돌릴 것이 없어 곧바로 FAILED가 된다")
        void stock_failure_ends_without_compensation() {
            Order order = placeOrder(COUPON_ID);

            orderProductSaga.stockFailed(
                    stockResponse(order, StockStatus.FAILED, "PRODUCT_OUT_OF_STOCK"));

            Order failed = reload(order);
            assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(failed.getFailureCode()).isEqualTo("PRODUCT_OUT_OF_STOCK");
            // 보상 요청을 만들지 않았다
            assertThat(productOutboxHelper.find(order.getSagaId(), StockOrderStatus.CANCELLED))
                    .isEmpty();
        }

        @Test
        @DisplayName("쿠폰 실패가 보상을 시작하고 재고 복구 요청을 적재한다")
        void coupon_failure_starts_compensation() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));

            orderCouponSaga.couponFailed(
                    couponResponse(order, CouponStatus.FAILED, "COUPON_ALREADY_USED"));

            Order compensating = reload(order);
            assertThat(compensating.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
            assertThat(compensating.getFailureCode()).isEqualTo("COUPON_ALREADY_USED");

            assertThat(productOutboxHelper.find(order.getSagaId(), StockOrderStatus.CANCELLED)
                    .orElseThrow().getSagaStatus())
                    .isEqualTo(SagaStatus.COMPENSATING);
        }

        @Test
        @DisplayName("정상 요청과 보상 요청 Outbox가 같은 sagaId 아래 공존한다")
        void normal_and_compensation_outboxes_coexist() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));
            orderCouponSaga.couponFailed(
                    couponResponse(order, CouponStatus.FAILED, "COUPON_ALREADY_USED"));

            // UNIQUE(saga_id, request_status)가 없으면 이 INSERT가 충돌해
            // 보상이 영원히 발행되지 않는다 — 정상 시나리오로는 절대 드러나지 않는 버그다
            assertThat(productOutboxRepository.findAll()).hasSize(2);
            assertThat(productOutboxHelper.find(order.getSagaId(), StockOrderStatus.PENDING)).isPresent();
            assertThat(productOutboxHelper.find(order.getSagaId(), StockOrderStatus.CANCELLED)).isPresent();
        }

        @Test
        @DisplayName("보상이 끝나면 원래 실패 원인을 유지한 채 FAILED가 된다")
        void compensation_completes_keeping_original_cause() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));
            orderCouponSaga.couponFailed(
                    couponResponse(order, CouponStatus.FAILED, "COUPON_ALREADY_USED"));

            orderProductSaga.stockRestored(stockResponse(order, StockStatus.RESTORED, null));

            Order failed = reload(order);
            assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
            // 사가를 무너뜨린 원인은 "재고를 되돌렸다"가 아니라 "쿠폰이 이미 사용됨"이다.
            // 여기서 fail()을 썼다면 이 값이 덮였을 것이다.
            assertThat(failed.getFailureCode()).isEqualTo("COUPON_ALREADY_USED");
            assertThat(productOutboxHelper.find(order.getSagaId(), StockOrderStatus.CANCELLED)
                    .orElseThrow().getSagaStatus())
                    .isEqualTo(SagaStatus.COMPENSATED);
        }

        @Test
        @DisplayName("복구까지 실패하면 주문이 COMPENSATING에 멈춘다 — 자동 회복하지 않는다")
        void restore_failure_leaves_order_compensating() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));
            orderCouponSaga.couponFailed(
                    couponResponse(order, CouponStatus.FAILED, "COUPON_ALREADY_USED"));

            orderProductSaga.stockFailed(
                    stockResponse(order, StockStatus.FAILED, "PRODUCT_NOT_FOUND"));

            Order stuck = reload(order);
            assertThat(stuck.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
            assertThat(stuck.getFailureCode()).isEqualTo("COUPON_ALREADY_USED");
        }
    }

    @Nested
    @DisplayName("멱등성 — 중복·지연 응답")
    class Idempotency {

        @Test
        @DisplayName("같은 재고 응답이 두 번 와도 총액이 두 번 반영되지 않는다")
        void duplicate_stock_response_is_ignored() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));

            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));

            Order after = reload(order);
            assertThat(after.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
            assertThat(after.getTotalAmount()).isEqualTo(TOTAL);
            // 쿠폰 요청도 한 번만 적재된다
            assertThat(couponOutboxRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("같은 쿠폰 응답이 두 번 와도 할인이 두 번 반영되지 않는다")
        void duplicate_coupon_response_is_ignored() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));
            orderCouponSaga.couponApplied(couponResponse(order, CouponStatus.APPLIED, null));

            orderCouponSaga.couponApplied(couponResponse(order, CouponStatus.APPLIED, null));

            Order after = reload(order);
            assertThat(after.getDiscountAmount()).isEqualTo(2_000L);
            assertThat(after.getFinalAmount()).isEqualTo(TOTAL - 2_000L);
        }

        @Test
        @DisplayName("종료된 사가에 늦게 도착한 응답은 조용히 무시된다")
        void late_response_after_completion_is_ignored() {
            Order order = placeOrder(null);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));
            assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.COMPLETED);

            // 기대 상태 조회에서 걸러지므로 도메인 가드까지 가지 않는다
            orderProductSaga.stockRestored(stockResponse(order, StockStatus.RESTORED, null));
            orderProductSaga.stockFailed(stockResponse(order, StockStatus.FAILED, "LATE"));

            Order after = reload(order);
            assertThat(after.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(after.getFailureCode()).isNull();
        }

        @Test
        @DisplayName("보낸 적 없는 쿠폰 복구 응답은 주문을 건드리지 않는다")
        void unsent_coupon_restore_response_is_ignored() {
            Order order = placeOrder(COUPON_ID);
            orderProductSaga.stockReserved(stockResponse(order, StockStatus.RESERVED, null));

            orderCouponSaga.couponRestored(couponResponse(order, CouponStatus.RESTORED, null));

            assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
        }
    }
}
