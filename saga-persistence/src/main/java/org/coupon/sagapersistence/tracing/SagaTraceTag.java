package org.coupon.sagapersistence.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaTraceTag {

    public static final String SAGA_CONSUME = "saga.consume";

    private static final String SAGA_ID = "saga.id";

    private final ObjectProvider<Tracer> tracerProvider;

    public SagaSpan open(String name, UUID sagaId) {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return SagaSpan.NOOP;
        }

        Span span = tracer.nextSpan().name(name);
        if (sagaId != null) {
            span.tag(SAGA_ID, sagaId.toString());
        }
        span.start();
        return new SagaSpan(span, tracer.withSpan(span));
    }

    public static final class SagaSpan implements AutoCloseable {

        private static final SagaSpan NOOP = new SagaSpan(null, null);

        private final Span span;
        private final Tracer.SpanInScope scope;

        private SagaSpan(Span span, Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        public void close() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        }
    }
}
