package org.coupon.orderservice.saga.framework;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.common.saga.SagaStatus;
import org.coupon.common.saga.SagaStepStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "saga_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaState implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Version
    @Column(name = "version")
    private int version;

    @Column(name = "type", updatable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private String payload;

    @Column(name = "current_step")
    private String currentStep;

    @Convert(converter = StepStatusConverter.class)
    @Column(name = "step_status")
    private Map<String, SagaStepStatus> stepStatus = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SagaStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private boolean isNew = true;

    private SagaState(Long orderId, String type, String payload) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.type = type;
        this.payload = payload;
        this.status = SagaStatus.STARTED;
        this.stepStatus = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SagaState start(Long orderId, String type, String payload) {
        return new SagaState(orderId, type, payload);
    }

    public Map<String, SagaStepStatus> getStepStatus() {
        return Collections.unmodifiableMap(stepStatus);
    }

    void updateStepStatus(String stepId, SagaStepStatus stepStatus) {
        Map<String, SagaStepStatus> next = new LinkedHashMap<>(this.stepStatus);
        next.put(stepId, stepStatus);
        this.stepStatus = next;
        touch();
    }

    void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
        touch();
    }

    void setStatus(SagaStatus status) {
        this.status = status;
        touch();
    }

    void changePayload(String payload) {
        this.payload = payload;
        touch();
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
