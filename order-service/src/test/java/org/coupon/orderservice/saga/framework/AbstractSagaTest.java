package org.coupon.orderservice.saga.framework;

import org.coupon.common.saga.SagaStatus;
import org.coupon.common.saga.SagaStepStatus;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractSagaTest {

    record TestPayload(String note) {
    }

    @SagaDefinition(type = "test-saga", stepIds = {"a", "b", "c"})
    static class ThreeStepSaga extends AbstractSaga {

        ThreeStepSaga(SagaState state, SagaContext context) {
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

        void changeNote(String note) {
            updatePayload(new TestPayload(note));
        }
    }

    static class NoDefinitionSaga extends AbstractSaga {
        NoDefinitionSaga(SagaState state, SagaContext context) {
            super(state, context);
        }

        protected SagaStepMessage stepMessage(String stepId) {
            return null;
        }

        protected SagaStepMessage compensatingStepMessage(String stepId) {
            return null;
        }
    }

    private final SagaPayloadCodec codec = new SagaPayloadCodec();
    private final List<SagaStepEvent> published = new ArrayList<>();
    private SagaState state;
    private ThreeStepSaga saga;

    @BeforeEach
    void setUp() {
        state = SagaState.start(100L, "test-saga", codec.serialize(new TestPayload("hello")));
        saga = new ThreeStepSaga(state, new SagaContext(event -> published.add((SagaStepEvent) event), codec));
    }

    @Nested
    @DisplayName("전진")
    class Advance {

        @Test
        @DisplayName("시작하면 첫 스텝이 STARTED 가 되고 요청 1건이 사가 ID 를 키로 발행된다")
        void start_publishes_first_step() {
            saga.start();

            assertThat(saga.getCurrentStep()).isEqualTo("a");
            assertThat(saga.getStepStatus()).containsExactly(Map.entry("a", SagaStepStatus.STARTED));
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(published).singleElement().satisfies(e -> {
                assertThat(e.aggregateType()).isEqualTo("a");
                assertThat(e.aggregateId()).isEqualTo(state.getId().toString());
                assertThat(e.type()).isEqualTo("REQUEST");
                assertThat(e.payload()).isEqualTo(new TestPayload("hello"));
            });
        }

        @Test
        @DisplayName("a→b→c 가 전부 SUCCEEDED 면 COMPLETED 이고 요청은 정확히 3건이다")
        void succeeds_through_all_steps() {
            saga.start();
            saga.onStepResult("a", SagaStepStatus.SUCCEEDED);
            saga.onStepResult("b", SagaStepStatus.SUCCEEDED);
            saga.onStepResult("c", SagaStepStatus.SUCCEEDED);

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(saga.getCurrentStep()).isNull();
            assertThat(saga.getStepStatus()).containsOnly(
                    Map.entry("a", SagaStepStatus.SUCCEEDED),
                    Map.entry("b", SagaStepStatus.SUCCEEDED),
                    Map.entry("c", SagaStepStatus.SUCCEEDED));
            assertThat(published).extracting(SagaStepEvent::aggregateType).containsExactly("a", "b", "c");
            assertThat(published).extracting(SagaStepEvent::type).containsOnly("REQUEST");
        }

        @Test
        @DisplayName("isAwaiting 은 STARTED·COMPENSATING 인 스텝만 참이다")
        void awaiting_only_for_in_flight_steps() {
            saga.start();
            assertThat(saga.isAwaiting("a")).isTrue();
            assertThat(saga.isAwaiting("b")).isFalse();

            saga.onStepResult("a", SagaStepStatus.SUCCEEDED);
            assertThat(saga.isAwaiting("a")).isFalse();
            assertThat(saga.isAwaiting("b")).isTrue();
        }
    }

    @Nested
    @DisplayName("보상")
    class Compensation {

        @Test
        @DisplayName("c 가 FAILED 면 b→a 순서로 CANCEL 이 나가고 끝에 ABORTED 가 된다")
        void compensates_backwards() {
            saga.start();
            saga.onStepResult("a", SagaStepStatus.SUCCEEDED);
            saga.onStepResult("b", SagaStepStatus.SUCCEEDED);

            saga.onStepResult("c", SagaStepStatus.FAILED);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.ABORTING);
            assertThat(saga.getCurrentStep()).isEqualTo("b");
            assertThat(saga.getStepStatus().get("b")).isEqualTo(SagaStepStatus.COMPENSATING);
            assertThat(published.get(3)).satisfies(e -> {
                assertThat(e.aggregateType()).isEqualTo("b");
                assertThat(e.type()).isEqualTo("CANCEL");
            });

            saga.onStepResult("b", SagaStepStatus.COMPENSATED);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.ABORTING);
            assertThat(saga.getCurrentStep()).isEqualTo("a");
            assertThat(published.get(4).aggregateType()).isEqualTo("a");
            assertThat(published.get(4).type()).isEqualTo("CANCEL");

            saga.onStepResult("a", SagaStepStatus.COMPENSATED);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.ABORTED);
            assertThat(saga.getCurrentStep()).isNull();
            assertThat(saga.getStepStatus()).containsOnly(
                    Map.entry("a", SagaStepStatus.COMPENSATED),
                    Map.entry("b", SagaStepStatus.COMPENSATED),
                    Map.entry("c", SagaStepStatus.FAILED));
            assertThat(published).hasSize(5);
        }

        @Test
        @DisplayName("첫 스텝이 FAILED 면 되돌릴 게 없어 곧장 ABORTED 이고 추가 발행이 없다")
        void first_step_failure_aborts_immediately() {
            saga.start();

            saga.onStepResult("a", SagaStepStatus.FAILED);

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.ABORTED);
            assertThat(saga.getCurrentStep()).isNull();
            assertThat(saga.getStepStatus()).containsExactly(Map.entry("a", SagaStepStatus.FAILED));
            assertThat(published).hasSize(1);
        }

        @Test
        @DisplayName("기다리지 않는 스텝의 결과(지연·중복 응답)는 거부하고 요청을 다시 내지 않는다")
        void rejects_result_for_step_not_awaiting() {
            saga.start();
            saga.onStepResult("a", SagaStepStatus.SUCCEEDED);

            assertThatThrownBy(() -> saga.onStepResult("a", SagaStepStatus.SUCCEEDED))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> saga.onStepResult("c", SagaStepStatus.SUCCEEDED))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(published).hasSize(2);
            assertThat(saga.getCurrentStep()).isEqualTo("b");
        }

        @Test
        @DisplayName("정의에 없는 스텝 결과는 거부한다")
        void rejects_unknown_step() {
            saga.start();

            assertThatThrownBy(() -> saga.onStepResult("zzz", SagaStepStatus.SUCCEEDED))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("deriveStatus 4분기")
    class DeriveStatus {

        @Test
        void all_succeeded_is_completed() {
            state.updateStepStatus("a", SagaStepStatus.SUCCEEDED);
            state.updateStepStatus("b", SagaStepStatus.SUCCEEDED);
            assertThat(saga.deriveStatus()).isEqualTo(SagaStatus.COMPLETED);
        }

        @Test
        void any_started_is_started() {
            state.updateStepStatus("a", SagaStepStatus.SUCCEEDED);
            state.updateStepStatus("b", SagaStepStatus.STARTED);
            assertThat(saga.deriveStatus()).isEqualTo(SagaStatus.STARTED);
        }

        @Test
        void any_compensating_is_aborting() {
            state.updateStepStatus("a", SagaStepStatus.COMPENSATING);
            state.updateStepStatus("b", SagaStepStatus.FAILED);
            assertThat(saga.deriveStatus()).isEqualTo(SagaStatus.ABORTING);
        }

        @Test
        void only_failed_or_compensated_is_aborted() {
            state.updateStepStatus("a", SagaStepStatus.COMPENSATED);
            state.updateStepStatus("b", SagaStepStatus.FAILED);
            assertThat(saga.deriveStatus()).isEqualTo(SagaStatus.ABORTED);
        }

        @Test
        void empty_is_started() {
            assertThat(saga.deriveStatus()).isEqualTo(SagaStatus.STARTED);
        }
    }

    @Nested
    @DisplayName("payload 와 정의")
    class PayloadAndDefinition {

        @Test
        @DisplayName("updatePayload 이후의 스텝 메시지는 갱신된 payload 를 싣는다")
        void updated_payload_flows_into_next_message() {
            saga.start();
            saga.changeNote("changed");

            saga.onStepResult("a", SagaStepStatus.SUCCEEDED);

            assertThat(published.get(1).payload()).isEqualTo(new TestPayload("changed"));
            assertThat(codec.deserialize(state.getPayload(), TestPayload.class).note()).isEqualTo("changed");
        }

        @Test
        @DisplayName("@SagaDefinition 이 없는 클래스는 만들 수 없다")
        void requires_definition() {
            assertThatThrownBy(() -> new NoDefinitionSaga(state, new SagaContext(e -> { }, codec)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
