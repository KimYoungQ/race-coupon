package org.coupon.sagapersistence.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.DomainEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    static final String SPAN_NAME = "outbox.write";
    private static final String SAGA_ID_TAG = "saga.id";

    private final OutboxEventRepository outboxEventRepository;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final ObjectProvider<Tracer> tracerProvider;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : tracer.nextSpan().name(SPAN_NAME).tag(SAGA_ID_TAG, event.aggregateId()).start();
        try (Tracer.SpanInScope ignored = span == null ? null : tracer.withSpan(span)) {
            OutboxEvent saved = outboxEventRepository.save(new OutboxEvent(
                    UUID.randomUUID(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.type(),
                    sagaPayloadCodec.serialize(event.payload())));
            log.debug("outbox 적재: id={}, aggregatetype={}, aggregateid={}, type={}",
                    saved.getId(), saved.getAggregateType(), saved.getAggregateId(), saved.getType());
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }
}
