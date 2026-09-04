package org.coupon.orderservice.saga.framework;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaManager {

    private final SagaStateRepository sagaStateRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SagaPayloadCodec sagaPayloadCodec;

    @Transactional(propagation = Propagation.MANDATORY)
    public <S extends AbstractSaga> S begin(Long orderId, Class<S> sagaClass, Object payload, SagaFactory<S> factory) {
        SagaDefinition definition = sagaClass.getAnnotation(SagaDefinition.class);
        if (definition == null) {
            throw new IllegalArgumentException("@SagaDefinition 이 없는 사가 클래스: " + sagaClass.getName());
        }
        SagaState state = sagaStateRepository.save(
                SagaState.start(orderId, definition.type(), sagaPayloadCodec.serialize(payload)));
        S saga = factory.create(state, context());
        saga.start();
        log.info("사가 시작: sagaId={}, type={}, orderId={}, firstStep={}",
                state.getId(), definition.type(), orderId, state.getCurrentStep());
        return saga;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <S extends AbstractSaga> Optional<S> find(
            UUID sagaId, Function<String, ? extends SagaFactory<? extends S>> factoryByType) {
        return sagaStateRepository.findById(sagaId).map((SagaState state) -> {
            SagaFactory<? extends S> factory = factoryByType.apply(state.getType());
            return factory.create(state, context());
        });
    }

    private SagaContext context() {
        return new SagaContext(eventPublisher, sagaPayloadCodec);
    }
}
