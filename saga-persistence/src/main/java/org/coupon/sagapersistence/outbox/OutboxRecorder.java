package org.coupon.sagapersistence.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.DomainEvent;
import org.coupon.sagapersistence.tracing.SagaTraceTag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    static final String SPAN_NAME = "outbox.write";
    static final String TRACEPARENT = "traceparent";
    static final String TRACESTATE = "tracestate";

    private final OutboxEventRepository outboxEventRepository;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final ObjectProvider<Tracer> tracerProvider;
    private final ObjectProvider<Propagator> propagatorProvider;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(DomainEvent event) {
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : startSpan(tracer, event);
        try (Tracer.SpanInScope ignored = span == null ? null : tracer.withSpan(span)) {
            Map<String, String> traceContext = injectTraceContext(span);
            OutboxEvent saved = outboxEventRepository.save(new OutboxEvent(
                    UUID.randomUUID(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.type(),
                    sagaPayloadCodec.serialize(event.payload()),
                    traceContext.get(TRACEPARENT),
                    traceContext.get(TRACESTATE)));
            log.debug("outbox 적재: id={}, aggregatetype={}, aggregateid={}, type={}, traceparent={}",
                    saved.getId(), saved.getAggregateType(), saved.getAggregateId(), saved.getType(),
                    saved.getTraceparent());
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }

    private Span startSpan(Tracer tracer, DomainEvent event) {
        return tracer.nextSpan()
                .name(SPAN_NAME)
                .tag(SagaTraceTag.TAG_SAGA_ID, event.aggregateId())
                .tag(SagaTraceTag.TAG_STEP, SagaTraceTag.stepTag(event.aggregateType()))
                .tag(SagaTraceTag.TAG_EVENT_TYPE, event.type())
                .start();
    }

    /**
     * 변환된 값은 outbox에 저장하고, 이후 Kafka로 전달한다.
     * 추적 기능이 없으면 빈 값으로 처리한다.
     */
    private Map<String, String> injectTraceContext(Span span) {
        Propagator propagator = propagatorProvider.getIfAvailable();
        if (span == null || propagator == null) {
            return Map.of();
        }

        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, (c, key, value) -> {
            if (c != null && value != null) {
                c.put(key, value);
            }
        });
        return carrier;
    }
}
