package org.coupon.couponservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.AggregateTypes;
import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.event.RequestType;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.coupon.sagapersistence.tracing.SagaTraceTag;
import org.coupon.sagapersistence.tracing.SagaTraceTag.SagaSpan;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponApplyListener {

    private final CouponApplyHandler couponApplyHandler;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final SagaTraceTag sagaTraceTag;

    @KafkaListener(
            id = "coupon-apply-request",
            groupId = "coupon-saga",
            topics = "coupon-apply.request",
            containerFactory = "sagaListenerContainerFactory")
    public void onCouponApplyRequest(@Header(KafkaHeaders.RECEIVED_KEY) String sagaId,
                                     @Header("id") byte[] eventId,
                                     @Payload String body) {
        String eventIdText = new String(eventId, StandardCharsets.UTF_8);
        CouponApplyRequestPayload request =
                sagaPayloadCodec.deserialize(body, CouponApplyRequestPayload.class);

        try (SagaSpan span = openSpan(request.type(), sagaId, eventIdText)) {
            try {
                describe(span, couponApplyHandler.handle(sagaId, eventIdText, request));
            } catch (RuntimeException e) {
                span.error(e);
                throw e;
            }
        }
    }

    /**
     * 요청 종류에 맞는 업무 스팬을 연다.
     * REQUEST와 CANCEL은 서로 다른 스팬 이름을 사용한다.
     * 응답 outbox 처리까지 포함하기 위해 트랜잭션 전에 스팬을 연다.
     */
    private SagaSpan openSpan(RequestType type, String sagaId, String eventId) {
        boolean compensating = type == RequestType.CANCEL;
        String spanName = compensating
                ? SagaTraceTag.compensateSpanName(AggregateTypes.COUPON_APPLY)
                : SagaTraceTag.stepSpanName(AggregateTypes.COUPON_APPLY);
        String action = compensating ? SagaTraceTag.ACTION_COMPENSATE : SagaTraceTag.ACTION_EXECUTE;

        return sagaTraceTag.open(
                spanName,
                UUID.fromString(sagaId),
                SagaTraceTag.stepTag(AggregateTypes.COUPON_APPLY),
                action,
                eventId);
    }

    /**
     * 처리 결과를 스팬에 기록한다.
     * 이미 처리한 요청은 skipped로 기록하고,
     * 쿠폰 거절은 오류가 아닌 처리 결과로 기록한다.
     */
    private void describe(SagaSpan span, CouponApplyResponsePayload response) {
        if (response == null) {
            span.result(SagaTraceTag.RESULT_SKIPPED);
            return;
        }
        if (response.result() == CouponResult.REJECTED) {
            span.failed(response.failureCode());
            return;
        }
        span.result(SagaTraceTag.RESULT_SUCCESS);
    }
}
