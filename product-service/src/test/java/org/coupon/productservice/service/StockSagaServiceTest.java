package org.coupon.productservice.service;

import org.coupon.common.event.RequestType;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.domain.ReservationStatus;
import org.coupon.productservice.domain.StockReservation;
import org.coupon.productservice.messaging.StockRequestHandler;
import org.coupon.productservice.repository.ProductRepository;
import org.coupon.productservice.repository.StockReservationRepository;
import org.coupon.productservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.idempotency.ConsumedMessageRepository;
import org.coupon.sagapersistence.outbox.OutboxEvent;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainer.class)
class StockSagaServiceTest {

    private static final long ORDER_ID = 100L;
    private static final long INITIAL_STOCK = 10L;

    @Autowired
    private StockRequestHandler stockRequestHandler;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ConsumedMessageRepository consumedMessageRepository;

    @Autowired
    private SagaPayloadCodec sagaPayloadCodec;

    private Long productId;
    private String sagaId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(Product.builder()
                .name("무선 이어폰")
                .price(10_000L)
                .stock(INITIAL_STOCK)
                .build()).getId();
        sagaId = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAllInBatch();
        consumedMessageRepository.deleteAllInBatch();
        stockReservationRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("재고 예약")
    class Reserve {

        @Test
        @DisplayName("예약에 성공하면 재고 차감, 예약, 처리 원장, 응답 outbox가 함께 남는다")
        void reserveSuccess() {
            // given
            String eventId = UUID.randomUUID().toString();

            // when
            stockRequestHandler.handle(sagaId, eventId, request(RequestType.REQUEST, 2));

            // then
            assertThat(currentStock()).isEqualTo(8L);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID)).isPresent();
            assertThat(consumedMessageRepository.existsById(eventId)).isTrue();
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(onlyResponse().result()).isEqualTo(StockResult.RESERVED);
        }

        @Test
        @DisplayName("재고가 부족하면 재고도 예약도 남기지 않고 OUT_OF_STOCK 응답만 남긴다")
        void reserveOutOfStock() {
            // given
            int tooMany = 999;

            // when
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.REQUEST, tooMany));

            // then
            assertThat(currentStock()).isEqualTo(INITIAL_STOCK);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID)).isEmpty();
            assertThat(onlyResponse().result()).isEqualTo(StockResult.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("같은 주문이 다른 eventId로 다시 와도 재고를 두 번 깎지 않는다")
        void duplicateRequestDeductsOnce() {
            // given
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.REQUEST, 2));
            outboxEventRepository.deleteAllInBatch();

            // when
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.REQUEST, 2));

            // then
            assertThat(currentStock()).isEqualTo(8L);
            assertThat(stockReservationRepository.count()).isEqualTo(1);
            assertThat(onlyResponse().result()).isEqualTo(StockResult.RESERVED);
        }

        @Test
        @DisplayName("동시에 예약해도 재고보다 많이 예약되지 않는다")
        void concurrentReserveNeverExceedsStock() throws InterruptedException {
            // given
            int requestCount = 20;
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(requestCount);

            // when
            for (int i = 0; i < requestCount; i++) {
                long orderId = 1000L + i;
                executor.submit(() -> {
                    try {
                        stockRequestHandler.handle(
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(),
                                request(RequestType.REQUEST, 1, orderId));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then
            assertThat(failures).isEmpty();
            assertThat(currentStock()).isZero();

            List<StockReservation> reservations = stockReservationRepository.findAll();
            assertThat(reservations).hasSize((int) INITIAL_STOCK);
            assertThat(reservations).allMatch(it -> it.getStatus() == ReservationStatus.RESERVED);

            assertThat(countResponses(StockResult.RESERVED)).isEqualTo(INITIAL_STOCK);
            assertThat(countResponses(StockResult.OUT_OF_STOCK)).isEqualTo(requestCount - INITIAL_STOCK);
        }
    }

    @Nested
    @DisplayName("재고 복원")
    class Release {

        @Test
        @DisplayName("예약을 취소하면 재고가 원래 수량으로 복원된다")
        void releaseRestoresStock() {
            // given
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.REQUEST, 2));
            assertThat(currentStock()).isEqualTo(8L);
            outboxEventRepository.deleteAllInBatch();

            // when
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.CANCEL, 2));

            // then
            assertThat(currentStock()).isEqualTo(INITIAL_STOCK);
            assertThat(onlyResponse().result()).isEqualTo(StockResult.RELEASED);
        }

        @Test
        @DisplayName("취소를 두 번 받아도 재고는 한 번만 복원되고 응답도 한 건씩만 남는다")
        void duplicateReleaseRestoresOnce() {
            // given
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.REQUEST, 2));
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.CANCEL, 2));
            long outboxAfterFirstRelease = outboxEventRepository.count();

            // when
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.CANCEL, 2));

            // then
            assertThat(currentStock()).isEqualTo(INITIAL_STOCK);
            assertThat(outboxEventRepository.count()).isEqualTo(outboxAfterFirstRelease + 1);
        }

        @Test
        @DisplayName("복구 수량은 취소 메시지가 아니라 예약에 남은 수량을 따른다")
        void releaseRestoresReservedQuantity() {
            // given
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.REQUEST, 2));

            // when
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.CANCEL, 99));

            // then
            assertThat(currentStock()).isEqualTo(INITIAL_STOCK);
        }
    }

    private StockReservationRequestPayload request(RequestType type, int quantity) {
        return request(type, quantity, ORDER_ID);
    }

    private StockReservationRequestPayload request(RequestType type, int quantity, long orderId) {
        return new StockReservationRequestPayload(orderId, productId, quantity, type);
    }

    private long currentStock() {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private long countResponses(StockResult result) {
        return outboxEventRepository.findAll().stream()
                .map(this::responseOf)
                .filter(it -> it.result() == result)
                .count();
    }

    private StockReservationResponsePayload onlyResponse() {
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        return responseOf(outboxEventRepository.findAll().get(0));
    }

    private StockReservationResponsePayload responseOf(OutboxEvent event) {
        return sagaPayloadCodec.deserialize(event.getPayload(), StockReservationResponsePayload.class);
    }
}
