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

/**
 * 사가의 첫 단추 — 주문 접수가 재고 요청 발행까지 이어지는지 본다.
 *
 * <p>여기서 확인하는 것은 네 가지다.
 * <ol>
 *   <li>주문과 Outbox가 <b>같은 트랜잭션</b>에서 함께 저장된다</li>
 *   <li>주문 접수만으로는 <b>아무것도 발행되지 않는다</b> — 적재와 발행이 분리돼 있다</li>
 *   <li>스케줄러 폴링이 {@code product-request}로 실제 메시지를 내보낸다</li>
 *   <li>파티션 키가 {@code productId}다 — 이게 재고 직렬화 전략의 전제다</li>
 * </ol>
 *
 * <p>테스트 프로파일은 백그라운드 타이머를 사실상 꺼두고(주기 1시간), 발행이 필요한 시점에
 * {@link ProductOutboxScheduler#publishPending()}을 직접 호출한다. 타이머를 기다리지 않아
 * 빠르고, 같은 row가 두 번 발행되는 일이 없어 관측이 흔들리지 않는다.
 */
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
        // deleteAllInBatch는 cascade를 타지 않아 order_item의 FK에 걸린다. 품목은 orphanRemoval로 지운다.
        orderRepository.deleteAll();

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "saga-start-test", true);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "org.coupon.common.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StockRequest.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        consumer = new DefaultKafkaConsumerFactory<String, StockRequest>(
                props, new StringDeserializer(), new JsonDeserializer<>(StockRequest.class, false))
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, SagaTopics.PRODUCT_REQUEST);

        // 임베디드 브로커는 클래스 안의 테스트들이 공유한다. 앞선 테스트가 남긴 레코드를 비워
        // 각 테스트가 깨끗한 지점에서 시작하게 한다 — 특히 "아무것도 발행되지 않는다"를
        // 확인하는 테스트는 이걸 빼면 남의 메시지를 보고 실패한다.
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
        // 정상 요청이므로 PENDING이다. 보상 요청과 구분하는 유일한 기준이다.
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

        // 같은 상품의 예약과 복구가 한 파티션에서 직렬화되려면 키가 productId여야 한다
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

        // 발행 콜백은 Kafka IO 스레드에서 비동기로 돌아 상태 갱신이 조금 늦는다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ProductOutbox outbox = productOutboxRepository.findAll().get(0);
            assertThat(outbox.getOutboxStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getProcessedAt()).isNotNull();
            assertThat(outbox.getPublishAttempts()).isPositive();
        });

        // 사가 진행도는 응답을 받기 전까지 STARTED 그대로다 — 발행 여부와 축이 다르다
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

        // 스케줄러가 배치를 forEach로 돌기 때문에, 여기서 예외가 새면 뒤의 row들이 볼모가 된다
        assertThatCode(() -> sagaRequestPublisher.publish(outboxId, SagaChannel.PRODUCT))
                .doesNotThrowAnyException();

        // 발행되지 않았으니 STARTED로 남아 다음 폴링이 다시 집는다 — 유실이 아니다
        assertThat(productOutboxRepository.findById(outboxId).orElseThrow().getOutboxStatus())
                .isEqualTo(OutboxStatus.STARTED);
        assertThat(orderRepository.findById(response.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("깨진 row 한 건이 같은 배치의 나머지 발행을 막지 않는다")
    void poison_row_does_not_block_the_batch() {
        // 깨진 주문을 먼저 만들어 배치에서 앞에 오게 한다
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

    /** 역직렬화가 깨지는 상황을 만든다. 엔티티에 setter가 없어 DB를 직접 건드린다. */
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

        // 메시지 id와 Outbox PK가 같아야 로그 한 줄로 DB row와 Kafka 메시지를 이어 볼 수 있다
        assertThat(request.id()).isEqualTo(outbox.getId());
        assertThat(request.quantity()).isEqualTo(3);
    }
}
