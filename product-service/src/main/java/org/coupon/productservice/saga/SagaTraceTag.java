package org.coupon.productservice.saga;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 사가 경계마다 스팬을 하나 열고 {@code saga.id} 태그를 붙인다.
 *
 * <p>Jaeger는 DB를 읽지 않는다 — 스팬에 실려 온 것만 색인한다. Outbox에 {@code sagaId} 컬럼이
 * 이미 있어도 Jaeger 검색창에는 나오지 않으므로, 추적용으로 <b>같은 값을 스팬 태그에 복사</b>한다.
 * payload와 스키마는 건드리지 않는다.
 *
 * <p>태그가 붙는 경계는 셋이다.
 * <pre>
 *   outbox.write     Outbox 저장   (요청 스레드)
 *   outbox.publish   Outbox 발행   (스케줄러 스레드)
 *   saga.consume     Kafka 소비    (리스너 스레드)
 * </pre>
 *
 * <p><b>트레이스를 하나로 잇지는 않는다.</b> Outbox 경계에서 원 요청의 맥락은 이미 사라졌고
 * 그것을 되살리려면 맥락을 row에 저장해야 한다(별도 작업). 여기서 얻는 것은
 * {@code saga.id=<uuid>} 한 번 검색으로 <b>세 조각이 한 리스트에 모이는 것</b>이다.
 *
 * <p>{@link #open(String, UUID)}이 {@code nextSpan()}을 쓰므로 현재 스팬이 있으면 그 자식이 된다.
 * 그래서 {@code outbox.write}는 HTTP 요청 스팬 아래에 자연히 중첩되고,
 * 나머지 둘은 각자 루트가 된다(그 스레드에는 상위 맥락이 없다).
 *
 * <p><b>{@code Tracer}를 {@link ObjectProvider}로 받는다.</b> 벤치마크 실행 시
 * {@code -Dmanagement.tracing.enabled=false}로 추적을 끄면 그 빈이 아예 만들어지지 않는데,
 * 직접 주입하면 그때 이 서비스가 기동조차 못 한다. 없으면 조용히 no-op이 된다.
 */
@Component
@RequiredArgsConstructor
public class SagaTraceTag {

    public static final String OUTBOX_WRITE = "outbox.write";
    public static final String OUTBOX_PUBLISH = "outbox.publish";
    public static final String SAGA_CONSUME = "saga.consume";

    /** Jaeger Tags 칸에 {@code saga.id=<uuid>}로 검색하는 키. */
    private static final String SAGA_ID = "saga.id";

    private final ObjectProvider<Tracer> tracerProvider;

    /**
     * 스팬을 열고 {@code saga.id}를 붙인다. 반드시 닫아야 하므로 try-with-resources로 쓴다.
     *
     * <p>{@code sagaId}가 {@code null}이어도 스팬은 연다 — 태그만 빠진다. 사가를 특정할 수 없는
     * 경로에서도 구간 자체는 보이는 편이 진단에 낫다.
     */
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

    /** {@link SagaTraceTag#open} 이 돌려주는 스코프. 닫으면 스팬까지 함께 끝낸다. */
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
