package org.coupon.orderservice.saga.framework;

import org.coupon.common.saga.SagaStatus;
import org.coupon.common.saga.SagaStepStatus;
import org.coupon.orderservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlTestContainer.class)
class SagaManagerTest {

    record TestPayload(Long orderId) {
    }

    @SagaDefinition(type = "test-two-step", stepIds = {"a", "b"})
    static class TwoStepSaga extends AbstractSaga {

        TwoStepSaga(SagaState state, SagaContext context) {
            super(state, context);
        }

        @Override
        protected SagaStepMessage stepMessage(String stepId) {
            return new SagaStepMessage(stepId, "REQUEST", getPayload(TestPayload.class));
        }

        @Override
        protected SagaStepMessage compensatingStepMessage(String stepId) {
            return new SagaStepMessage(stepId, "CANCEL", getPayload(TestPayload.class));
        }
    }

    @Autowired
    private SagaManager sagaManager;

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        sagaStateRepository.deleteAllInBatch();
    }

    private UUID begin(long orderId) {
        return transactionTemplate.execute(status ->
                sagaManager.begin(orderId, TwoStepSaga.class, new TestPayload(orderId), TwoStepSaga::new).getId());
    }

    @Test
    @DisplayName("begin 은 saga_state 와 첫 스텝 outbox 행을 같은 트랜잭션에 남기고, 키는 사가 ID 다")
    void begin_persists_state_and_first_request() {
        UUID sagaId = begin(1L);

        SagaState state = sagaStateRepository.findById(sagaId).orElseThrow();
        assertThat(state.getOrderId()).isEqualTo(1L);
        assertThat(state.getType()).isEqualTo("test-two-step");
        assertThat(state.getStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(state.getCurrentStep()).isEqualTo("a");
        assertThat(state.getStepStatus()).containsExactly(Map.entry("a", SagaStepStatus.STARTED));
        assertThat(state.getPayload()).contains("\"orderId\"");

        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getAggregateType()).isEqualTo("a");
            assertThat(row.getAggregateId()).isEqualTo(sagaId.toString());
            assertThat(row.getType()).isEqualTo("REQUEST");
        });
    }

    @Test
    @DisplayName("같은 orderId 로 두 번째 사가를 시작하면 order_id 유니크 제약이 막는다")
    void second_saga_for_same_order_is_rejected() {
        begin(2L);

        assertThatThrownBy(() -> begin(2L)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(sagaStateRepository.findByOrderId(2L)).isPresent();
        assertThat(sagaStateRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("복원한 사가에 스텝 결과를 넣으면 상태·step_status·version 이 갱신되고 다음 요청이 적재된다")
    void restored_saga_advances_and_persists() {
        UUID sagaId = begin(3L);
        int versionBefore = sagaStateRepository.findById(sagaId).orElseThrow().getVersion();

        transactionTemplate.executeWithoutResult(status ->
                sagaManager.<TwoStepSaga>find(sagaId, type -> TwoStepSaga::new).orElseThrow()
                        .onStepResult("a", SagaStepStatus.SUCCEEDED));

        SagaState state = sagaStateRepository.findById(sagaId).orElseThrow();
        assertThat(state.getCurrentStep()).isEqualTo("b");
        assertThat(state.getStepStatus()).containsOnly(
                Map.entry("a", SagaStepStatus.SUCCEEDED), Map.entry("b", SagaStepStatus.STARTED));
        assertThat(state.getVersion()).isGreaterThan(versionBefore);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("트랜잭션 없이 begin 하면 예외다")
    void begin_requires_transaction() {
        assertThatThrownBy(() ->
                sagaManager.begin(4L, TwoStepSaga.class, new TestPayload(4L), TwoStepSaga::new))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
