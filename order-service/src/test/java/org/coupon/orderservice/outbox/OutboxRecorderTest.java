package org.coupon.orderservice.outbox;

import org.coupon.common.event.DomainEvent;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.outbox.OutboxEvent;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlTestContainer.class)
class OutboxRecorderTest {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        orderRepository.deleteAll();
    }

    private DomainEvent event(String aggregateId) {
        return new DomainEvent() {
            public String aggregateType() { return "stock-reservation"; }
            public String aggregateId() { return aggregateId; }
            public String type() { return "REQUEST"; }
            public Object payload() { return new Payload(1L, 2); }
        };
    }

    record Payload(Long orderId, Integer quantity) {
    }

    @Test
    @DisplayName("도메인 변경과 outbox 행이 같은 트랜잭션에 커밋된다")
    void commits_domain_and_outbox_together() {
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.save(Order.builder().userId(1L).productId(7L).quantity(2).build());
            eventPublisher.publishEvent(event("saga-1"));
        });

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getId().toString()).hasSize(36);
            assertThat(row.getAggregateType()).isEqualTo("stock-reservation");
            assertThat(row.getAggregateId()).isEqualTo("saga-1");
            assertThat(row.getType()).isEqualTo("REQUEST");
            assertThat(row.getPayload()).contains("\"orderId\"").contains("\"quantity\"");
            assertThat(row.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("이벤트 발행 뒤 트랜잭션이 깨지면 주문도 outbox 도 남지 않는다 (3-write 롤백)")
    void rollback_leaves_neither_domain_nor_outbox() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderRepository.save(Order.builder().userId(1L).productId(7L).quantity(2).build());
            eventPublisher.publishEvent(event("saga-2"));
            throw new IllegalStateException("커밋 직전 고의 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행한 이벤트는 outbox 에 적재되지 않는다 — 발행 지점은 반드시 @Transactional 안")
    void publish_outside_transaction_records_nothing() {
        eventPublisher.publishEvent(event("saga-3"));

        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("payload 는 JSON 컬럼에서 그대로 읽힌다")
    void payload_round_trips_as_json() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event("saga-4")));

        OutboxEvent row = outboxEventRepository.findAll().get(0);
        assertThat(new org.coupon.sagapersistence.outbox.SagaPayloadCodec()
                .deserialize(row.getPayload(), Payload.class)).isEqualTo(new Payload(1L, 2));
    }
}
