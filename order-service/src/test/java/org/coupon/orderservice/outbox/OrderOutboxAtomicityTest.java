package org.coupon.orderservice.outbox;

import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.repository.OrderItemRepository;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.saga.OrderSagaPayload;
import org.coupon.orderservice.saga.StockOnlyOrderSaga;
import org.coupon.orderservice.saga.framework.SagaManager;
import org.coupon.orderservice.saga.framework.SagaStateRepository;
import org.coupon.orderservice.service.OrderService;
import org.coupon.orderservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.outbox.OutboxEvent;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Import({MySqlTestContainer.class, OrderOutboxAtomicityTest.FailingOrderWriter.class})
class OrderOutboxAtomicityTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private FailingOrderWriter failingOrderWriter;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAllInBatch();
        sagaStateRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("주문을 생성하면 주문, 사가 상태, 재고 요청 outbox가 한 트랜잭션에 함께 저장된다")
    void orderAndOutboxCommitTogether() {
        // given
        OrderCreateRequest request = new OrderCreateRequest(7L, 2, null);

        // when
        Long orderId = orderService.create(42L, request).orderId();

        // then
        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(sagaStateRepository.findByOrderId(orderId)).isPresent();
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getAggregateType()).isEqualTo("stock-reservation");
            assertThat(row.getType()).isEqualTo("REQUEST");
        });
    }

    @Test
    @DisplayName("주문 저장 뒤 예외가 나면 주문도 사가 상태도 outbox도 남지 않는다")
    void exceptionRollsBackOrderAndOutbox() {
        // given
        Order order = Order.builder().userId(42L).productId(7L).quantity(2).build();

        // when
        Throwable thrown = catchThrowable(() -> failingOrderWriter.saveOrderThenFail(order));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(orderRepository.count()).isZero();
        assertThat(sagaStateRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    /**
     * 주문 생성과 똑같은 순서로 저장하다가 커밋 직전에 예외를 던지는 테스트용 경계.
     */
    public static class FailingOrderWriter {

        private final OrderRepository orderRepository;
        private final SagaManager sagaManager;

        public FailingOrderWriter(OrderRepository orderRepository, SagaManager sagaManager) {
            this.orderRepository = orderRepository;
            this.sagaManager = sagaManager;
        }

        @Transactional
        public void saveOrderThenFail(Order order) {
            Order saved = orderRepository.save(order);
            sagaManager.begin(saved.getId(), StockOnlyOrderSaga.class,
                    OrderSagaPayload.of(saved), StockOnlyOrderSaga::new);
            throw new IllegalStateException("커밋 직전 고의 실패");
        }
    }
}
