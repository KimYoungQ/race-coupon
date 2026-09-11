package org.coupon.orderservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.coupon.sagapersistence.tracing.SagaTraceTag;
import org.coupon.sagapersistence.tracing.SagaTraceTag.SagaSpan;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.coupon.orderservice.saga.AbstractOrderSaga.COUPON_APPLY;
import static org.coupon.orderservice.saga.AbstractOrderSaga.STOCK_RESERVATION;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaResponseListener {

    private final SagaResponseHandler sagaResponseHandler;
    private final SagaTraceTag sagaTraceTag;

    @KafkaListener(
            id = "order-stock-response",
            groupId = "order-saga",
            topics = "stock-reservation.response",
            containerFactory = "sagaListenerContainerFactory")
    public void onStockResponse(@Header(KafkaHeaders.RECEIVED_KEY) String sagaId,
                                @Header("id") byte[] eventId,
                                @Payload String body) {
        String eventIdValue = new String(eventId, StandardCharsets.UTF_8);
        try (SagaSpan span = openSpan(STOCK_RESERVATION, sagaId, eventIdValue)) {
            try {
                describe(span, sagaResponseHandler.onStockResponse(sagaId, eventIdValue, body));
            } catch (InvalidOrderStateException e) {
                span.result(SagaTraceTag.RESULT_SKIPPED);
                log.error("주문 상태와 사가 상태가 어긋난 재고 응답, 건너뜀: sagaId={}, eventId={}", sagaId, eventIdValue, e);
            } catch (RuntimeException e) {
                span.error(e);
                throw e;
            }
        }
    }

    @KafkaListener(
            id = "order-coupon-response",
            groupId = "order-saga",
            topics = "coupon-apply.response",
            containerFactory = "sagaListenerContainerFactory")
    public void onCouponResponse(@Header(KafkaHeaders.RECEIVED_KEY) String sagaId,
                                 @Header("id") byte[] eventId,
                                 @Payload String body) {
        String eventIdValue = new String(eventId, StandardCharsets.UTF_8);
        try (SagaSpan span = openSpan(COUPON_APPLY, sagaId, eventIdValue)) {
            try {
                describe(span, sagaResponseHandler.onCouponResponse(sagaId, eventIdValue, body));
            } catch (InvalidOrderStateException e) {
                span.result(SagaTraceTag.RESULT_SKIPPED);
                log.error("주문 상태와 사가 상태가 어긋난 쿠폰 응답, 건너뜀: sagaId={}, eventId={}", sagaId, eventIdValue, e);
            } catch (RuntimeException e) {
                span.error(e);
                throw e;
            }
        }
    }

    /**
     * 응답 처리 스팬을 연다.
     * Kafka에서 이어받은 trace 안에서 자식 스팬으로 생성된다.
     */
    private SagaSpan openSpan(String step, String sagaId, String eventId) {
        return sagaTraceTag.open(
                SagaTraceTag.stepSpanName(step),
                UUID.fromString(sagaId),
                SagaTraceTag.stepTag(step),
                SagaTraceTag.ACTION_HANDLE_RESPONSE,
                eventId);
    }

    /**
     * 처리 결과를 스팬에 기록한다.
     * 이미 처리한 요청은 skipped로 기록하고,
     * 응답 처리 자체는 성공으로 기록한다.
     * 실제 처리 결과와 실패 사유는 별도로 남긴다.
     */
    private void describe(SagaSpan span, SagaResponseOutcome outcome) {
        if (outcome.skipped()) {
            span.result(SagaTraceTag.RESULT_SKIPPED);
            return;
        }

        span.result(SagaTraceTag.RESULT_SUCCESS);
        span.tag(SagaTraceTag.TAG_STEP_RESULT, outcome.stepResult());
        span.tag(SagaTraceTag.TAG_FAILURE_CODE, outcome.failureCode());
        if (outcome.isTerminal()) {
            span.status(outcome.sagaStatus().name());
        }
    }
}
