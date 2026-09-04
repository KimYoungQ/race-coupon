package org.coupon.productservice.service;

import org.coupon.common.event.AggregateTypes;
import org.coupon.common.event.RequestType;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.common.exception.ErrorCode;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.domain.ReservationStatus;
import org.coupon.productservice.messaging.StockRequestHandler;
import org.coupon.productservice.repository.ProductRepository;
import org.coupon.productservice.repository.StockReservationRepository;
import org.coupon.productservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.idempotency.ConsumedMessageRepository;
import org.coupon.sagapersistence.outbox.OutboxEvent;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Import(MySqlTestContainer.class)
class StockSagaServiceTest {

    private static final long ORDER_ID = 100L;

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
        outboxEventRepository.deleteAllInBatch();
        consumedMessageRepository.deleteAllInBatch();
        stockReservationRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        productId = productRepository.save(Product.builder()
                .name("무선 이어폰")
                .price(10_000L)
                .stock(10L)
                .build()).getId();
        sagaId = UUID.randomUUID().toString();
    }

    private String body(Long productId, int quantity, RequestType type) {
        return sagaPayloadCodec.serialize(new StockReservationRequestPayload(ORDER_ID, productId, quantity, type));
    }

    private String handle(RequestType type, int quantity) {
        return handle(type, productId, quantity);
    }

    private String handle(RequestType type, Long productId, int quantity) {
        String eventId = UUID.randomUUID().toString();
        stockRequestHandler.handle(sagaId, eventId, body(productId, quantity, type));
        return eventId;
    }

    private long stock() {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private OutboxEvent onlyOutbox() {
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        return outboxEventRepository.findAll().get(0);
    }

    private StockReservationResponsePayload responseOf(OutboxEvent event) {
        return sagaPayloadCodec.deserialize(event.getPayload(), StockReservationResponsePayload.class);
    }

    @Nested
    @DisplayName("재고 예약(REQUEST)")
    class Reserve {

        @Test
        @DisplayName("재고를 깎고 예약을 남기며, 응답 outbox 와 처리 원장이 같은 트랜잭션에 남는다")
        void reserves_and_records_response_outbox() {
            String eventId = handle(RequestType.REQUEST, 2);

            assertThat(stock()).isEqualTo(8L);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID)).isPresent();

            OutboxEvent event = onlyOutbox();
            assertThat(event.getAggregateType()).isEqualTo(AggregateTypes.STOCK_RESERVATION);
            assertThat(event.getAggregateId()).isEqualTo(sagaId);
            assertThat(event.getType()).isEqualTo("RESERVED");
            StockReservationResponsePayload response = responseOf(event);
            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            assertThat(response.result()).isEqualTo(StockResult.RESERVED);
            assertThat(response.productName()).isEqualTo("무선 이어폰");
            assertThat(response.unitPrice()).isEqualTo(10_000L);
            assertThat(response.failureCode()).isNull();

            assertThat(consumedMessageRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("재고가 부족하면 예외가 아니라 OUT_OF_STOCK 응답이고 재고는 그대로다")
        void out_of_stock_becomes_rejection_response() {
            assertThatCode(() -> handle(RequestType.REQUEST, 999)).doesNotThrowAnyException();

            assertThat(stock()).isEqualTo(10L);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID)).isEmpty();

            StockReservationResponsePayload response = responseOf(onlyOutbox());
            assertThat(response.result()).isEqualTo(StockResult.OUT_OF_STOCK);
            assertThat(response.failureCode()).isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode());
        }

        @Test
        @DisplayName("존재하지 않는 상품도 OUT_OF_STOCK + PRODUCT_NOT_FOUND 로 답한다")
        void unknown_product_becomes_rejection_response() {
            assertThatCode(() -> handle(RequestType.REQUEST, 999_999L, 1)).doesNotThrowAnyException();

            StockReservationResponsePayload response = responseOf(onlyOutbox());
            assertThat(response.result()).isEqualTo(StockResult.OUT_OF_STOCK);
            assertThat(response.failureCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND.getCode());
        }
    }

    @Nested
    @DisplayName("멱등성 — 같은 메시지(id 헤더)의 재전송")
    class Idempotency {

        @Test
        @DisplayName("같은 eventId 로 두 번 받아도 재고는 한 번만 깎이고 응답도 한 건이다")
        void same_event_id_is_processed_once() {
            String eventId = UUID.randomUUID().toString();
            String body = body(productId, 2, RequestType.REQUEST);

            stockRequestHandler.handle(sagaId, eventId, body);
            stockRequestHandler.handle(sagaId, eventId, body);

            assertThat(stock()).isEqualTo(8L);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(consumedMessageRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("재고 복구(CANCEL)")
    class Release {

        @Test
        @DisplayName("예약을 되돌리고 재고를 원복하며 RELEASED 로 답한다")
        void releases_stock() {
            handle(RequestType.REQUEST, 2);
            outboxEventRepository.deleteAllInBatch();

            handle(RequestType.CANCEL, 2);

            assertThat(stock()).isEqualTo(10L);
            assertThat(stockReservationRepository.findByOrderId(ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.RESTORED);
            OutboxEvent event = onlyOutbox();
            assertThat(event.getType()).isEqualTo("RELEASED");
            assertThat(responseOf(event).result()).isEqualTo(StockResult.RELEASED);
        }

        @Test
        @DisplayName("복구를 두 번 받아도(다른 eventId) 재고가 두 번 늘지 않는다")
        void duplicate_release_does_not_add_twice() {
            handle(RequestType.REQUEST, 2);
            handle(RequestType.CANCEL, 2);
            handle(RequestType.CANCEL, 2);

            assertThat(stock()).isEqualTo(10L);
            assertThat(outboxEventRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("되돌릴 예약이 없어도 RELEASED 로 답한다 — 실패로 답하면 보상이 끝나지 못한다")
        void release_without_reservation_still_succeeds() {
            handle(RequestType.CANCEL, 2);

            assertThat(responseOf(onlyOutbox()).result()).isEqualTo(StockResult.RELEASED);
            assertThat(stock()).isEqualTo(10L);
        }
    }
}
