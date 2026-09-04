package org.coupon.orderservice.saga.framework;

import lombok.extern.slf4j.Slf4j;
import org.coupon.common.saga.SagaStatus;
import org.coupon.common.saga.SagaStepStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractSaga {

    private final SagaState state;
    private final SagaContext context;
    private final String type;
    private final List<String> stepIds;

    protected AbstractSaga(SagaState state, SagaContext context) {
        SagaDefinition definition = getClass().getAnnotation(SagaDefinition.class);
        if (definition == null) {
            throw new IllegalStateException("@SagaDefinition 이 없는 사가 클래스: " + getClass().getName());
        }
        if (definition.stepIds().length == 0) {
            throw new IllegalStateException("stepIds 가 비어 있는 사가 클래스: " + getClass().getName());
        }
        this.state = state;
        this.context = context;
        this.type = definition.type();
        this.stepIds = List.of(definition.stepIds());
    }

    public UUID getId() {
        return state.getId();
    }

    public Long getOrderId() {
        return state.getOrderId();
    }

    public String getType() {
        return type;
    }

    public SagaStatus getStatus() {
        return state.getStatus();
    }

    public String getCurrentStep() {
        return state.getCurrentStep();
    }

    public Map<String, SagaStepStatus> getStepStatus() {
        return state.getStepStatus();
    }

    public boolean isAwaiting(String stepId) {
        SagaStepStatus current = state.getStepStatus().get(stepId);
        return current == SagaStepStatus.STARTED || current == SagaStepStatus.COMPENSATING;
    }

    protected <T> T getPayload(Class<T> payloadType) {
        return context.codec().deserialize(state.getPayload(), payloadType);
    }

    protected void updatePayload(Object payload) {
        state.changePayload(context.codec().serialize(payload));
    }

    protected abstract SagaStepMessage stepMessage(String stepId);

    protected abstract SagaStepMessage compensatingStepMessage(String stepId);

    public void start() {
        advance();
    }

    public void onStepResult(String stepId, SagaStepStatus stepStatus) {
        if (!stepIds.contains(stepId)) {
            throw new IllegalArgumentException("사가 " + type + " 에 없는 스텝: " + stepId);
        }
        if (!isAwaiting(stepId)) {
            throw new IllegalStateException("사가 " + state.getId() + " 는 스텝 " + stepId
                    + " 의 결과를 기다리지 않는다: stepStatus=" + state.getStepStatus());
        }
        state.updateStepStatus(stepId, stepStatus);
        switch (stepStatus) {
            case SUCCEEDED -> advance();
            case FAILED, COMPENSATED -> goBack();
            case STARTED, COMPENSATING -> {
            }
        }
    }

    private void advance() {
        String next = nextStep();
        if (next == null) {
            state.setStatus(SagaStatus.COMPLETED);
            state.setCurrentStep(null);
            log.info("사가 완료: sagaId={}, type={}", state.getId(), type);
            return;
        }
        SagaStepMessage message = stepMessage(next);
        state.updateStepStatus(next, SagaStepStatus.STARTED);
        state.setCurrentStep(next);
        state.setStatus(deriveStatus());
        publish(message);
    }

    private void goBack() {
        String previous = previousStep();
        if (previous == null) {
            state.setStatus(SagaStatus.ABORTED);
            state.setCurrentStep(null);
            log.info("사가 중단: sagaId={}, type={}, stepStatus={}", state.getId(), type, state.getStepStatus());
            return;
        }
        SagaStepMessage message = compensatingStepMessage(previous);
        state.updateStepStatus(previous, SagaStepStatus.COMPENSATING);
        state.setCurrentStep(previous);
        state.setStatus(deriveStatus());
        publish(message);
    }

    private void publish(SagaStepMessage message) {
        context.eventPublisher().publishEvent(new SagaStepEvent(
                message.aggregateType(), state.getId().toString(), message.type(), message.payload()));
    }

    SagaStatus deriveStatus() {
        Collection<SagaStepStatus> statuses = state.getStepStatus().values();
        if (statuses.isEmpty()) {
            return SagaStatus.STARTED;
        }
        if (statuses.stream().allMatch(s -> s == SagaStepStatus.SUCCEEDED)) {
            return SagaStatus.COMPLETED;
        }
        if (statuses.contains(SagaStepStatus.STARTED)) {
            return SagaStatus.STARTED;
        }
        if (statuses.contains(SagaStepStatus.COMPENSATING)) {
            return SagaStatus.ABORTING;
        }
        return SagaStatus.ABORTED;
    }

    private String nextStep() {
        String current = state.getCurrentStep();
        if (current == null) {
            return stepIds.get(0);
        }
        int index = stepIds.indexOf(current);
        return index == stepIds.size() - 1 ? null : stepIds.get(index + 1);
    }

    private String previousStep() {
        String current = state.getCurrentStep();
        if (current == null) {
            return null;
        }
        int index = stepIds.indexOf(current);
        return index <= 0 ? null : stepIds.get(index - 1);
    }
}
