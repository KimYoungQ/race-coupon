package org.coupon.orderservice.saga;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockRequest;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderStatus;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.kafka.SagaRequestPublisher;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.repository.ProductOutboxRepository;
import org.coupon.orderservice.service.OrderService;
import org.coupon.orderservice.service.outbox.ProductOutboxScheduler;
import org.coupon.orderservice.service.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = SagaTopics.PRODUCT_REQUEST)
class OrderSagaStartTest {

    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final long COUPON_ID = 9L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductOutboxRepository productOutboxRepository;

    @Autowired
    private SagaPayloadCodec sagaPayloadCodec;

    @Autowired
    private ProductOutboxScheduler productOutboxScheduler;

    @Autowired
    private SagaRequestPublisher sagaRequestPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, StockRequest> consumer;

    @BeforeEach
    void setUp() {
        productOutboxRepository.deleteAllInBatch();
        orderRepository.deleteAll();

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "saga-start-test", true);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "org.coupon.common.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StockRequest.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        consumer = new DefaultKafkaConsumerFactory<String, StockRequest>(
                props, new StringDeserializer(), new JsonDeserializer<>(StockRequest.class, false))
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, SagaTopics.PRODUCT_REQUEST);

        KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("주문 접수가 ORDERS와 product_outbox를 함께 남긴다")
    void create_persists_order_and_outbox_together() {
        OrderCreateResponse response = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, 2, COUPON_ID));

        Order order = orderRepository.findById(response.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getSagaId()).isNotNull();

        List<ProductOutbox> outboxes = productOutboxRepository.findAll();
        assertThat(outboxes).hasSize(1);

        ProductOutbox outbox = outboxes.get(0);
        assertThat(outbox.getSagaId()).isEqualTo(order.getSagaId());
        assertThat(outbox.getOrderId()).isEqualTo(order.getId());
        assertThat(outbox.getRequestStatus()).isEqualTo(StockOrderStatus.PENDING);
        assertThat(outbox.getSagaStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(outbox.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("주문 접수만으로는 아무것도 발행되지 않는다 — 적재와 발행이 분리돼 있다")
    void create_alone_publishes_nothing() {
        orderService.create(USER_ID, new OrderCreateRequest(PRODUCT_ID, 2, COUPON_ID));

        assertThat(KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2)).isEmpty())
                .as("스케줄러가 돌기 전에는 메시지가 나가면 안 된다")
                .isTrue();
        assertThat(productOutboxRepository.findAll().get(0).getOutboxStatus())
                .isEqualTo(OutboxStatus.STARTED);
    }

    @Test
    @DisplayName("스케줄러 폴링이 product-request로 재고 요청을 내보내고 파티션 키가 productId다")
    void publishes_stock_request_keyed_by_product_id() {
        OrderCreateResponse response = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, 2, COUPON_ID));
        Order order = orderRepository.findById(response.orderId()).orElseThrow();

        productOutboxScheduler.publishPending();

        ConsumerRecord<String, StockRequest> record =
                KafkaTestUtils.getSingleRecord(consumer, SagaTopics.PRODUCT_REQUEST, Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(String.valueOf(PRODUCT_ID));

        StockRequest request = record.value();
        assertThat(request.sagaId()).isEqualTo(order.getSagaId());
        assertThat(request.orderId()).isEqualTo(order.getId());
        assertThat(request.productId()).isEqualTo(PRODUCT_ID);
        assertThat(request.quantity()).isEqualTo(2);
        assertThat(request.stockOrderStatus()).isEqualTo(StockOrderStatus.PENDING);
        assertThat(request.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("발행에 성공하면 Outbox가 STARTED에서 COMPLETED로 넘어간다")
    void outbox_transitions_to_completed_after_publish() {
        OrderCreateResponse response = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, 2, COUPON_ID));

        productOutboxScheduler.publishPending();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ProductOutbox outbox = productOutboxRepository.findAll().get(0);
            assertThat(outbox.getOutboxStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getProcessedAt()).isNotNull();
            assertThat(outbox.getPublishAttempts()).isPositive();
        });

        assertThat(productOutboxRepository.findAll().get(0).getSagaStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(orderRepository.findById(response.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("payload가 깨져도 발행 호출이 예외를 밖으로 내보내지 않는다")
    void publish_never_throws_on_corrupt_payload() {
        OrderCreateResponse response = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, 1, null));
        UUID outboxId = productOutboxRepository.findAll().get(0).getId();
        corruptPayload(outboxId);

        assertThatCode(() -> sagaRequestPublisher.publish(outboxId, SagaChannel.PRODUCT))
                .doesNotThrowAnyException();

        assertThat(productOutboxRepository.findById(outboxId).orElseThrow().getOutboxStatus())
                .isEqualTo(OutboxStatus.STARTED);
        assertThat(orderRepository.findById(response.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("깨진 row 한 건이 같은 배치의 나머지 발행을 막지 않는다")
    void poison_row_does_not_block_the_batch() {
        OrderCreateResponse poisoned = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, 1, null));
        OrderCreateResponse healthy = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, 2, null));

        UUID poisonedOutboxId = outboxIdOf(poisoned.orderId());
        UUID healthyOutboxId = outboxIdOf(healthy.orderId());
        corruptPayload(poisonedOutboxId);

        productOutboxScheduler.publishPending();

        ConsumerRecord<String, StockRequest> record =
                KafkaTestUtils.getSingleRecord(consumer, SagaTopics.PRODUCT_REQUEST, Duration.ofSeconds(10));
        assertThat(record.value().orderId())
                .as("멀쩡한 요청은 앞의 실패와 무관하게 나가야 한다")
                .isEqualTo(healthy.orderId());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(productOutboxRepository.findById(healthyOutboxId).orElseThrow().getOutboxStatus())
                        .isEqualTo(OutboxStatus.COMPLETED));
        assertThat(productOutboxRepository.findById(poisonedOutboxId).orElseThrow().getOutboxStatus())
                .isEqualTo(OutboxStatus.STARTED);
    }

    private UUID outboxIdOf(Long orderId) {
        return productOutboxRepository.findAll().stream()
                .filter(outbox -> outbox.getOrderId().equals(orderId))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private void corruptPayload(UUID outboxId) {
        jdbcTemplate.update("UPDATE product_outbox SET payload = ? WHERE id = ?",
                "not-a-json", outboxId.toString());
    }

    @Test
    @DisplayName("Outbox payload는 그대로 요청 DTO로 되돌릴 수 있다")
    void payload_round_trips() {
        orderService.create(USER_ID, new OrderCreateRequest(PRODUCT_ID, 3, null));

        ProductOutbox outbox = productOutboxRepository.findAll().get(0);
        StockRequest request = sagaPayloadCodec.deserialize(outbox.getPayload(), StockRequest.class);

        assertThat(request.id()).isEqualTo(outbox.getId());
        assertThat(request.quantity()).isEqualTo(3);
    }
}
