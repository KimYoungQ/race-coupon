package org.coupon.productservice.service;

import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockRequest;
import org.coupon.common.event.StockResponse;
import org.coupon.common.event.StockStatus;
import org.coupon.common.exception.ErrorCode;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.domain.ReservationStatus;
import org.coupon.productservice.domain.outbox.OrderOutbox;
import org.coupon.productservice.repository.OrderOutboxRepository;
import org.coupon.productservice.repository.ProductRepository;
import org.coupon.productservice.repository.StockReservationRepository;
import org.coupon.productservice.service.outbox.OrderOutboxHelper;
import org.coupon.productservice.service.outbox.OrderOutboxPublishState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class StockSagaServiceTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private StockSagaService stockSagaService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private OrderOutboxRepository orderOutboxRepository;

    @Autowired
    private OrderOutboxHelper orderOutboxHelper;

    @Autowired
    private OrderOutboxPublishState orderOutboxPublishState;

    private Long productId;

    @BeforeEach
    void setUp() {
        orderOutboxRepository.deleteAllInBatch();
        stockReservationRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        productId = productRepository.save(Product.builder()
                .name("무선 이어폰")
                .price(10_000L)
                .stock(10L)
                .build()).getId();
    }

    private StockRequest request(StockOrderStatus status, int quantity) {
        return new StockRequest(UUID.randomUUID(), UUID.randomUUID(), ORDER_ID,
                productId, quantity, status, Instant.now());
    }

    private StockRequest sameSaga(StockRequest origin, StockOrderStatus status) {
        return new StockRequest(UUID.randomUUID(), origin.sagaId(), origin.orderId(),
                origin.productId(), origin.quantity(), status, Instant.now());
    }

    private StockResponse responseOf(OrderOutbox outbox) {
        return orderOutboxHelper.toResponse(outbox);
    }

    private OrderOutbox onlyOutbox() {
        assertThat(orderOutboxRepository.findAll()).hasSize(1);
        return orderOutboxRepository.findAll().get(0);
    }

    @Nested
    @DisplayName("재고 예약(PENDING)")
    class Reserve {

        @Test
        @DisplayName("재고를 깎고 예약을 남기며 단가 스냅샷을 응답에 싣는다")
        void reserves_and_replies_with_price_snapshot() {
            stockSagaService.handle(request(StockOrderStatus.PENDING, 2));

            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(8L);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID)).isPresent();

            StockResponse response = responseOf(onlyOutbox());
            assertThat(response.stockStatus()).isEqualTo(StockStatus.RESERVED);
            assertThat(response.productName()).isEqualTo("무선 이어폰");
            assertThat(response.unitPrice()).isEqualTo(10_000L);
            assertThat(response.failureMessages()).isEmpty();
        }

        @Test
        @DisplayName("재고가 부족하면 예외가 아니라 실패 응답이 된다")
        void out_of_stock_becomes_failure_response() {
            assertThatCode(() -> stockSagaService.handle(request(StockOrderStatus.PENDING, 999)))
                    .doesNotThrowAnyException();

            assertThat(productRepository.findById(productId).orElseThrow().getStock())
                    .as("실패했으므로 재고는 그대로여야 한다")
                    .isEqualTo(10L);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID)).isEmpty();

            StockResponse response = responseOf(onlyOutbox());
            assertThat(response.stockStatus()).isEqualTo(StockStatus.FAILED);
            assertThat(response.failureMessages())
                    .containsExactly(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode());
        }

        @Test
        @DisplayName("존재하지 않는 상품도 실패 응답으로 답한다")
        void unknown_product_becomes_failure_response() {
            StockRequest unknown = new StockRequest(UUID.randomUUID(), UUID.randomUUID(), ORDER_ID,
                    999_999L, 1, StockOrderStatus.PENDING, Instant.now());

            assertThatCode(() -> stockSagaService.handle(unknown)).doesNotThrowAnyException();

            assertThat(responseOf(onlyOutbox()).failureMessages())
                    .containsExactly(ErrorCode.PRODUCT_NOT_FOUND.getCode());
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("같은 요청을 다시 받아도 재고는 한 번만 깎인다")
        void duplicate_request_does_not_decrease_twice() {
            StockRequest first = request(StockOrderStatus.PENDING, 2);

            stockSagaService.handle(first);
            stockSagaService.handle(sameSaga(first, StockOrderStatus.PENDING));

            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(8L);
            assertThat(orderOutboxRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("중복 요청은 무시가 아니라 기존 응답의 재발행으로 이어진다")
        void duplicate_request_marks_existing_reply_for_republish() {
            StockRequest first = request(StockOrderStatus.PENDING, 2);
            stockSagaService.handle(first);

            UUID outboxId = onlyOutbox().getId();
            orderOutboxPublishState.markPublished(outboxId);
            assertThat(orderOutboxRepository.findById(outboxId).orElseThrow().getOutboxStatus())
                    .isEqualTo(OutboxStatus.COMPLETED);

            stockSagaService.handle(sameSaga(first, StockOrderStatus.PENDING));

            assertThat(orderOutboxRepository.findById(outboxId).orElseThrow().getOutboxStatus())
                    .as("조용히 무시하면 사가가 응답을 영영 기다린다")
                    .isEqualTo(OutboxStatus.STARTED);
        }
    }

    @Nested
    @DisplayName("재고 복구(CANCELLED)")
    class Restore {

        @Test
        @DisplayName("예약을 되돌리고 재고를 원복한다")
        void restores_stock() {
            StockRequest reserve = request(StockOrderStatus.PENDING, 2);
            stockSagaService.handle(reserve);

            stockSagaService.handle(sameSaga(reserve, StockOrderStatus.CANCELLED));

            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10L);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.RESTORED);
        }

        @Test
        @DisplayName("정상 응답과 보상 응답이 같은 sagaId 아래 함께 남는다")
        void normal_and_compensation_replies_coexist() {
            StockRequest reserve = request(StockOrderStatus.PENDING, 2);
            stockSagaService.handle(reserve);
            stockSagaService.handle(sameSaga(reserve, StockOrderStatus.CANCELLED));

            assertThat(orderOutboxRepository.findAll()).hasSize(2);
            assertThat(orderOutboxHelper.findProcessed(reserve.sagaId(), StockOrderStatus.PENDING))
                    .isPresent();
            assertThat(orderOutboxHelper.findProcessed(reserve.sagaId(), StockOrderStatus.CANCELLED))
                    .isPresent();
        }

        @Test
        @DisplayName("복구를 두 번 받아도 재고가 두 번 늘지 않는다")
        void duplicate_restore_does_not_add_twice() {
            StockRequest reserve = request(StockOrderStatus.PENDING, 2);
            stockSagaService.handle(reserve);
            stockSagaService.handle(sameSaga(reserve, StockOrderStatus.CANCELLED));
            stockSagaService.handle(sameSaga(reserve, StockOrderStatus.CANCELLED));

            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10L);
        }

        @Test
        @DisplayName("되돌릴 예약이 없어도 성공으로 답한다 — 실패로 답하면 보상이 끝나지 못한다")
        void restore_without_reservation_still_succeeds() {
            StockRequest restore = request(StockOrderStatus.CANCELLED, 2);

            stockSagaService.handle(restore);

            assertThat(responseOf(onlyOutbox()).stockStatus()).isEqualTo(StockStatus.RESTORED);
            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10L);
        }
    }
}
