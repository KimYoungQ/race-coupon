package org.coupon.sagapersistence.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * 사가 추적에 사용하는 이름과 태그를 관리한다.
 * 모든 서비스에서 같은 규칙을 사용하도록 한곳에서 관리한다.
 */
@Component
@RequiredArgsConstructor
public class SagaTraceTag {

    /** 사가 실행 식별자(업무 키). traceId와는 별개로 유지한다. */
    public static final String TAG_SAGA_ID = "saga.id";
    /** 사가 단계. aggregateType을 대문자 스네이크로 올린 값(STOCK_RESERVATION 등). */
    public static final String TAG_STEP = "saga.step";
    /** 이 스팬이 한 일: 실행 / 보상 / 응답 처리. */
    public static final String TAG_ACTION = "saga.action";
    /** 이 스팬의 결과: 성공 / 실패 / 건너뜀. */
    public static final String TAG_RESULT = "saga.result";
    /** 예상된 업무 거절 사유(OUT_OF_STOCK 등). 기술 예외가 아니라 결과 설명이다. */
    public static final String TAG_FAILURE_CODE = "saga.failure_code";
    /** 소비한 이벤트의 outbox id. 메시지 한 건과 스팬을 맞춰 보기 위한 값. */
    public static final String TAG_EVENT_ID = "event.id";
    /** 사가 전체 상태(COMPLETED/ABORTED). 오케스트레이터의 마지막 응답 처리 스팬에만 붙인다. */
    public static final String TAG_STATUS = "saga.status";
    /** 이벤트 타입 문자열. outbox.write 스팬에서 무엇을 실었는지 보기 위한 값. */
    public static final String TAG_EVENT_TYPE = "event.type";
    /** 응답이 전한 단계 결과(RESERVED/OUT_OF_STOCK/APPLIED/REJECTED). 응답 처리 성공 여부(saga.result)와는 별개다. */
    public static final String TAG_STEP_RESULT = "saga.step_result";

    public static final String ACTION_EXECUTE = "execute";
    public static final String ACTION_COMPENSATE = "compensate";
    public static final String ACTION_HANDLE_RESPONSE = "handle_response";

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILED = "failed";
    public static final String RESULT_SKIPPED = "skipped";

    private static final String STEP_SPAN_PREFIX = "saga.step.";
    private static final String COMPENSATE_SPAN_PREFIX = "saga.compensate.";

    private final ObjectProvider<Tracer> tracerProvider;

    /** 실행 스팬 이름: stock-reservation → saga.step.stock_reservation */
    public static String stepSpanName(String aggregateType) {
        return STEP_SPAN_PREFIX + underscored(aggregateType);
    }

    /** 보상 스팬 이름: stock-reservation → saga.compensate.stock_reservation */
    public static String compensateSpanName(String aggregateType) {
        return COMPENSATE_SPAN_PREFIX + underscored(aggregateType);
    }

    /** saga.step 태그 값: stock-reservation → STOCK_RESERVATION */
    public static String stepTag(String aggregateType) {
        return underscored(aggregateType).toUpperCase(Locale.ROOT);
    }

    private static String underscored(String aggregateType) {
        return aggregateType == null ? "" : aggregateType.replace('-', '_');
    }

    /**
     * 업무 스팬을 연다. {@code nextSpan()}이므로 지금 활성화된 스팬의 자식이 된다.
     * 리스너 안에서 부르면 Spring Kafka가 헤더로 복원한 소비 스팬이 부모가 되고,
     * 그 덕분에 사가 한 건이 트레이스 하나로 이어진다.
     */
    public SagaSpan open(String spanName, UUID sagaId, String stepTag, String action, String eventId) {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return SagaSpan.NOOP;
        }

        Span span = tracer.nextSpan().name(spanName);
        tagIfPresent(span, TAG_SAGA_ID, sagaId == null ? null : sagaId.toString());
        tagIfPresent(span, TAG_STEP, stepTag);
        tagIfPresent(span, TAG_ACTION, action);
        tagIfPresent(span, TAG_EVENT_ID, eventId);
        span.start();
        return new SagaSpan(span, tracer.withSpan(span));
    }

    private static void tagIfPresent(Span span, String key, String value) {
        if (value != null && !value.isBlank()) {
            span.tag(key, value);
        }
    }

    public static final class SagaSpan implements AutoCloseable {

        private static final SagaSpan NOOP = new SagaSpan(null, null);

        private final Span span;
        private final Tracer.SpanInScope scope;

        private SagaSpan(Span span, Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        /** 임의 태그. 규칙에 없는 값을 붙일 때만 쓴다. */
        public void tag(String key, String value) {
            if (span != null && key != null && value != null) {
                span.tag(key, value);
            }
        }

        /** saga.result 지정. */
        public void result(String result) {
            tag(TAG_RESULT, result);
        }

        /**
         * 예상된 업무 거절. 재고 부족처럼 시스템이 정상 동작한 결과이므로
         * span error가 아니라 결과와 사유 태그로만 남긴다.
         */
        public void failed(String failureCode) {
            tag(TAG_RESULT, RESULT_FAILED);
            tag(TAG_FAILURE_CODE, failureCode);
        }

        /** 사가 전체 상태(COMPLETED/ABORTED). */
        public void status(String sagaStatus) {
            tag(TAG_STATUS, sagaStatus);
        }

        /** 기술 예외. 이건 진짜 오류라서 span error로 기록한다. */
        public void error(Throwable throwable) {
            if (span != null) {
                span.error(throwable);
                span.tag(TAG_RESULT, RESULT_FAILED);
            }
        }

        @Override
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
