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

/**
 * 실제 Kafka를 거친 참여자 왕복. 지금까지의 테스트는 서비스를 직접 호출했으므로
 * <b>리스너·직렬화·파티션 배정이 실제로 도는지는 여기서 처음 확인된다.</b>
 *
 * <p>특히 마지막 테스트가 이 프로젝트의 핵심 주장을 검증한다 —
 * {@code Product}에 DB 락이 없는데도 동시 주문에서 재고가 정확한 이유는
 * {@code productId}를 파티션 키로 써서 같은 상품의 요청이 한 파티션에 직렬화되기 때문이다.
 * 그 전제가 실제로 성립하는지는 브로커 없이는 확인할 수 없다.
 */
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
        // 앞선 테스트가 남긴 레코드를 비운다
        KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    /** 파티션 키는 productId다 — 같은 상품의 요청이 한 파티션에서 직렬화되게 하는 값. */
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

        // 리스너가 소비해 재고를 깎고 응답 Outbox를 남길 때까지
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(stock()).isEqualTo(INITIAL_STOCK - 2);
            assertThat(orderOutboxRepository.findAll()).hasSize(1);
        });

        // 발행은 스케줄러만 한다. 테스트 프로파일은 타이머를 꺼뒀으므로 직접 돌린다.
        orderOutboxScheduler.publishPending();

        ConsumerRecord<String, StockResponse> record = KafkaTestUtils.getSingleRecord(
                consumer, SagaTopics.PRODUCT_RESPONSE, Duration.ofSeconds(15));

        // 응답 키는 orderId다 — 같은 주문의 결과가 조정자에서 순차 처리되도록
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

    /**
     * <b>이 프로젝트가 학습하려는 바로 그 지점.</b>
     *
     * <p>{@code Product}에는 비관적 락도 {@code @Version}도 없다. 그런데도 재고보다 많은 동시 주문에서
     * 초과 판매가 나지 않는 이유는 오직 하나다 — 모든 요청이 {@code productId}를 키로 발행되어
     * <b>같은 파티션에 들어가고, 한 컨슈머 스레드가 순서대로 처리</b>하기 때문이다.
     *
     * <p>토픽을 쪼개거나 파티션 키를 바꾸면 이 테스트가 깨진다. 그때 깨지는 것이 이 테스트의 존재 이유다.
     */
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

        // 같은 sagaId·같은 requestStatus로 다시 보낸다 (메시지 id만 다름)
        kafkaTemplate.send(SagaTopics.PRODUCT_REQUEST, String.valueOf(productId),
                new StockRequest(UUID.randomUUID(), sagaId, 300L, productId, 3,
                        StockOrderStatus.PENDING, Instant.now()));

        // 도메인은 다시 실행되지 않고, 기존 응답이 재발행 대상으로 표시된다
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
