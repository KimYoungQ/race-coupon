package org.coupon.productservice.service;

import org.coupon.common.event.RequestType;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.productservice.domain.Product;
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

import java.util.UUID;

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
        @DisplayName("재고가 부족하면 아무것도 차감하지 않고 OUT_OF_STOCK 응답을 남긴다")
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

            // when
            stockRequestHandler.handle(sagaId, UUID.randomUUID().toString(), request(RequestType.CANCEL, 2));

            // then
            assertThat(currentStock()).isEqualTo(INITIAL_STOCK);
            assertThat(outboxEventRepository.count()).isEqualTo(2);
            assertThat(lastResponse().result()).isEqualTo(StockResult.RELEASED);
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
    }

    private String request(RequestType type, int quantity) {
        return sagaPayloadCodec.serialize(new StockReservationRequestPayload(ORDER_ID, productId, quantity, type));
    }

    private long currentStock() {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private StockReservationResponsePayload onlyResponse() {
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        return responseOf(outboxEventRepository.findAll().get(0));
    }

    private StockReservationResponsePayload lastResponse() {
        var events = outboxEventRepository.findAll();
        return responseOf(events.get(events.size() - 1));
    }

    private StockReservationResponsePayload responseOf(OutboxEvent event) {
        return sagaPayloadCodec.deserialize(event.getPayload(), StockReservationResponsePayload.class);
    }
}
