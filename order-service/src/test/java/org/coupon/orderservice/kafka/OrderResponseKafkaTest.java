package org.coupon.orderservice.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockResponse;
import org.coupon.common.event.StockStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.OrderStatus;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.repository.CouponOutboxRepository;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.repository.ProductOutboxRepository;
import org.coupon.orderservice.service.OrderService;
import org.coupon.orderservice.service.outbox.CouponOutboxHelper;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실제 Kafka를 거친 오케스트레이터 왕복. 응답 리스너가 진짜로 도는지, 그리고
 * <b>처리에 실패한 레코드가 DLT로 빠지는지</b>를 본다.
 *
 * <p>DLT는 P6에서 붙인 것이고 원본 food-ordering-system에는 없다. 없으면 재시도 소진 후
 * 기본 recoverer가 로그만 남기고 레코드를 건너뛴다 — 조용한 유실이다.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3,
        topics = {SagaTopics.PRODUCT_REQUEST, SagaTopics.PRODUCT_RESPONSE,
                SagaTopics.PRODUCT_RESPONSE + ".DLT"})
class OrderResponseKafkaTest {

    private static final long USER_ID = 42L;
    private static final long PRODUCT_ID = 7L;
    private static final long COUPON_ID = 9L;
    private static final long UNIT_PRICE = 10_000L;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductOutboxRepository productOutboxRepository;

    @Autowired
    private CouponOutboxRepository couponOutboxRepository;

    @Autowired
    private CouponOutboxHelper couponOutboxHelper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, StockResponse> dltConsumer;

    @BeforeEach
    void setUp() {
        productOutboxRepository.deleteAllInBatch();
        couponOutboxRepository.deleteAllInBatch();
        orderRepository.deleteAll();

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "order-dlt-test", true);
        dltConsumer = new DefaultKafkaConsumerFactory<String, StockResponse>(
                props, new StringDeserializer(), new JsonDeserializer<>(StockResponse.class, false))
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, SagaTopics.PRODUCT_RESPONSE + ".DLT");
        KafkaTestUtils.getRecords(dltConsumer, Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    private Order placeOrder() {
        Long orderId = orderService.create(
                USER_ID, new OrderCreateRequest(PRODUCT_ID, 2, COUPON_ID)).orderId();
        return orderRepository.findById(orderId).orElseThrow();
    }

    /** 응답 키는 orderId다 — 같은 주문의 결과가 순차 처리되도록. */
    private void sendResponse(UUID sagaId, Long orderId, StockStatus status) {
        StockResponse response = new StockResponse(UUID.randomUUID(), sagaId, orderId, PRODUCT_ID,
                status,
                status == StockStatus.RESERVED ? "무선 이어폰" : null,
                status == StockStatus.RESERVED ? UNIT_PRICE : null,
                List.of(), Instant.now());
        kafkaTemplate.send(SagaTopics.PRODUCT_RESPONSE, String.valueOf(orderId), response);
    }

    @Test
    @DisplayName("재고 응답이 리스너를 거쳐 주문을 전진시키고 다음 요청을 적재한다")
    void response_advances_saga_through_kafka() {
        Order order = placeOrder();

        sendResponse(order.getSagaId(), order.getId(), StockStatus.RESERVED);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Order after = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
            assertThat(after.getTotalAmount()).isEqualTo(UNIT_PRICE * 2);
            // 다음 단계가 '기록'됐다
            assertThat(couponOutboxHelper.find(order.getSagaId(), CouponOrderStatus.PENDING))
                    .isPresent();
        });
    }

    /**
     * 처리에 실패하는 레코드를 만든다 — Outbox는 응답을 기다리고 있는데 주문이 존재하지 않는 상태.
     *
     * <p>{@code OrderNotFoundException}은 리스너가 삼키는 두 예외(확인된 중복)에 해당하지 않으므로
     * {@code BatchListenerFailedException}으로 올라가고, 재시도가 소진되면 DLT로 간다.
     */
    @Test
    @DisplayName("처리에 반복 실패한 레코드는 유실되지 않고 DLT로 간다")
    void unprocessable_record_goes_to_dlt() {
        Order order = placeOrder();
        Long missingOrderId = order.getId() + 9_999L;

        // sagaId는 맞아 Outbox 조회는 통과하고, orderId가 없어 findOrder에서 터진다
        sendResponse(order.getSagaId(), missingOrderId, StockStatus.RESERVED);

        ConsumerRecord<String, StockResponse> dlt = KafkaTestUtils.getSingleRecord(
                dltConsumer, SagaTopics.PRODUCT_RESPONSE + ".DLT", Duration.ofSeconds(30));

        assertThat(dlt.value().orderId()).isEqualTo(missingOrderId);
        // 원인이 헤더에 실려 온다 — 이게 없으면 왜 DLT에 있는지 알 수 없다
        assertThat(dlt.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();

        // 사가는 전진하지 않았다. DLT는 회복이 아니라 격리·관측 수단이다.
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("DLT로 빠진 레코드가 파티션을 막지 않는다 — 뒤따르는 응답은 정상 처리된다")
    void dlt_does_not_block_the_partition() {
        Order poisoned = placeOrder();
        Order healthy = placeOrder();

        sendResponse(poisoned.getSagaId(), poisoned.getId() + 9_999L, StockStatus.RESERVED);
        sendResponse(healthy.getSagaId(), healthy.getId(), StockStatus.RESERVED);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orderRepository.findById(healthy.getId()).orElseThrow().getStatus())
                        .as("앞 레코드가 막고 있으면 이 주문은 CREATED에 머문다")
                        .isEqualTo(OrderStatus.STOCK_RESERVED));

        assertThat(orderRepository.findById(poisoned.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }
}
