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
            assertThat(couponOutboxHelper.find(order.getSagaId(), CouponOrderStatus.PENDING))
                    .isPresent();
        });
    }

    @Test
    @DisplayName("처리에 반복 실패한 레코드는 유실되지 않고 DLT로 간다")
    void unprocessable_record_goes_to_dlt() {
        Order order = placeOrder();
        Long missingOrderId = order.getId() + 9_999L;

        sendResponse(order.getSagaId(), missingOrderId, StockStatus.RESERVED);

        ConsumerRecord<String, StockResponse> dlt = KafkaTestUtils.getSingleRecord(
                dltConsumer, SagaTopics.PRODUCT_RESPONSE + ".DLT", Duration.ofSeconds(30));

        assertThat(dlt.value().orderId()).isEqualTo(missingOrderId);
        assertThat(dlt.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();

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
