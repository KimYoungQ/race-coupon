package org.coupon.productservice.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockRequest;
import org.coupon.common.event.StockResponse;
import org.coupon.common.event.StockStatus;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.repository.OrderOutboxRepository;
import org.coupon.productservice.repository.ProductRepository;
import org.coupon.productservice.repository.StockReservationRepository;
import org.coupon.productservice.service.outbox.OrderOutboxScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3,
        topics = {SagaTopics.PRODUCT_REQUEST, SagaTopics.PRODUCT_RESPONSE})
class StockSagaKafkaTest {

    private static final long INITIAL_STOCK = 10L;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private OrderOutboxRepository orderOutboxRepository;

    @Autowired
    private OrderOutboxScheduler orderOutboxScheduler;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Long productId;
    private Consumer<String, StockResponse> consumer;

    @BeforeEach
    void setUp() {
        orderOutboxRepository.deleteAllInBatch();
        stockReservationRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        productId = productRepository.save(Product.builder()
                .name("무선 이어폰").price(10_000L).stock(INITIAL_STOCK).build()).getId();

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "stock-saga-kafka-test", true);
        consumer = new DefaultKafkaConsumerFactory<String, StockResponse>(
                props, new StringDeserializer(), new JsonDeserializer<>(StockResponse.class, false))
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, SagaTopics.PRODUCT_RESPONSE);
        KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    private void sendRequest(long orderId, int quantity, StockOrderStatus status) {
        StockRequest request = new StockRequest(UUID.randomUUID(), UUID.randomUUID(), orderId,
                productId, quantity, status, Instant.now());
        kafkaTemplate.send(SagaTopics.PRODUCT_REQUEST, String.valueOf(productId), request);
    }

    private long stock() {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    @Test
    @DisplayName("요청이 리스너를 거쳐 재고를 깎고 응답이 실제로 발행된다")
    void full_round_trip_through_kafka() {
        sendRequest(100L, 2, StockOrderStatus.PENDING);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(stock()).isEqualTo(INITIAL_STOCK - 2);
            assertThat(orderOutboxRepository.findAll()).hasSize(1);
        });

        orderOutboxScheduler.publishPending();

        ConsumerRecord<String, StockResponse> record = KafkaTestUtils.getSingleRecord(
                consumer, SagaTopics.PRODUCT_RESPONSE, Duration.ofSeconds(15));

        assertThat(record.key()).isEqualTo("100");

        StockResponse response = record.value();
        assertThat(response.stockStatus()).isEqualTo(StockStatus.RESERVED);
        assertThat(response.productName()).isEqualTo("무선 이어폰");
        assertThat(response.unitPrice()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("재고 부족도 예외가 아니라 실패 응답으로 실제 토픽에 나간다")
    void business_failure_is_published_as_response() {
        sendRequest(200L, 999, StockOrderStatus.PENDING);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orderOutboxRepository.findAll()).hasSize(1));
        orderOutboxScheduler.publishPending();

        ConsumerRecord<String, StockResponse> record = KafkaTestUtils.getSingleRecord(
                consumer, SagaTopics.PRODUCT_RESPONSE, Duration.ofSeconds(15));

        assertThat(record.value().stockStatus()).isEqualTo(StockStatus.FAILED);
        assertThat(record.value().failureMessages()).containsExactly("PRODUCT_OUT_OF_STOCK");
        assertThat(stock()).as("실패했으므로 재고는 그대로다").isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("재고 10에 20건이 동시에 몰려도 정확히 10건만 성공한다 — 초과 판매 0건")
    void concurrent_orders_never_oversell() {
        int attempts = 20;
        for (long orderId = 1; orderId <= attempts; orderId++) {
            sendRequest(orderId, 1, StockOrderStatus.PENDING);
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orderOutboxRepository.findAll()).hasSize(attempts));

        assertThat(stock()).as("남은 재고가 음수이거나 0이 아니면 lost update다").isZero();
        assertThat(stockReservationRepository.findAll())
                .as("예약은 재고만큼만 생겨야 한다")
                .hasSize((int) INITIAL_STOCK);

        long reserved = orderOutboxRepository.findAll().stream()
                .filter(outbox -> outbox.getStockStatus() == StockStatus.RESERVED).count();
        long failed = orderOutboxRepository.findAll().stream()
                .filter(outbox -> outbox.getStockStatus() == StockStatus.FAILED).count();

        assertThat(reserved).isEqualTo(INITIAL_STOCK);
        assertThat(failed).isEqualTo(attempts - INITIAL_STOCK);
    }

    @Test
    @DisplayName("같은 요청을 두 번 보내도 재고는 한 번만 깎이고 응답은 다시 나간다")
    void duplicate_request_over_kafka_is_idempotent() {
        UUID sagaId = UUID.randomUUID();
        StockRequest request = new StockRequest(UUID.randomUUID(), sagaId, 300L,
                productId, 3, StockOrderStatus.PENDING, Instant.now());

        kafkaTemplate.send(SagaTopics.PRODUCT_REQUEST, String.valueOf(productId), request);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orderOutboxRepository.findAll()).hasSize(1));
        orderOutboxScheduler.publishPending();
        KafkaTestUtils.getSingleRecord(consumer, SagaTopics.PRODUCT_RESPONSE, Duration.ofSeconds(15));

        kafkaTemplate.send(SagaTopics.PRODUCT_REQUEST, String.valueOf(productId),
                new StockRequest(UUID.randomUUID(), sagaId, 300L, productId, 3,
                        StockOrderStatus.PENDING, Instant.now()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orderOutboxRepository.findAll().get(0).getOutboxStatus().name())
                        .isEqualTo("STARTED"));
        assertThat(stock()).as("재고가 두 번 깎이면 안 된다").isEqualTo(INITIAL_STOCK - 3);
        assertThat(orderOutboxRepository.findAll()).hasSize(1);

        orderOutboxScheduler.publishPending();
        ConsumerRecords<String, StockResponse> republished =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15), 1);
        assertThat(republished.count())
                .as("조용히 무시하면 조정자가 응답을 영영 기다린다")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("정상 예약과 보상 복구가 같은 토픽으로 오가며 재고가 원복된다")
    void reserve_and_restore_over_same_topic() {
        long orderId = 400L;
        sendRequest(orderId, 4, StockOrderStatus.PENDING);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stock()).isEqualTo(INITIAL_STOCK - 4));

        sendRequest(orderId, 4, StockOrderStatus.CANCELLED);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(stock()).isEqualTo(INITIAL_STOCK);
            assertThat(orderOutboxRepository.findAll()).hasSize(2);
        });

        orderOutboxScheduler.publishPending();
        ConsumerRecords<String, StockResponse> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15), 2);

        List<StockStatus> statuses = new ArrayList<>();
        records.forEach(record -> statuses.add(record.value().stockStatus()));
        assertThat(statuses).containsExactlyInAnyOrder(StockStatus.RESERVED, StockStatus.RESTORED);
    }
}
