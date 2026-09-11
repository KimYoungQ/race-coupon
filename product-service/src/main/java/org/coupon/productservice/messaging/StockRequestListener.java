package org.coupon.productservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.AggregateTypes;
import org.coupon.common.event.RequestType;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
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
public class StockRequestListener {

    private final StockRequestHandler stockRequestHandler;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final SagaTraceTag sagaTraceTag;

    @KafkaListener(
            id = "product-stock-request",
            groupId = "product-saga",
            topics = "stock-reservation.request",
            containerFactory = "sagaListenerContainerFactory")
    public void onStockRequest(@Header(KafkaHeaders.RECEIVED_KEY) String sagaId,
                               @Header("id") byte[] eventId,
                               @Payload String body) {
        String eventIdText = new String(eventId, StandardCharsets.UTF_8);
        StockReservationRequestPayload request =
                sagaPayloadCodec.deserialize(body, StockReservationRequestPayload.class);

        try (SagaSpan span = openSpan(request.type(), sagaId, eventIdText)) {
            try {
                describe(span, stockRequestHandler.handle(sagaId, eventIdText, request));
            } catch (RuntimeException e) {
                span.error(e);
                throw e;
            }
        }
    }

    /**
     * 업무 스팬을 연다.
     * <p>
     * 같은 토픽으로 실행 요청과 보상 요청이 함께 오기 때문에 이름을 하나로 둘 수 없다.
     * 스팬은 한 번 열면 이름을 바꿀 수 없어서, 본문에서 요청 종류를 먼저 읽고 이름을 정한다.
     * 트랜잭션 바깥에서 열어야 커밋 직전에 쌓이는 응답 outbox.write 스팬까지 이 스팬의 자식이 된다.
     */
    private SagaSpan openSpan(RequestType type, String sagaId, String eventId) {
        boolean compensating = type == RequestType.CANCEL;
        String spanName = compensating
                ? SagaTraceTag.compensateSpanName(AggregateTypes.STOCK_RESERVATION)
                : SagaTraceTag.stepSpanName(AggregateTypes.STOCK_RESERVATION);
        String action = compensating ? SagaTraceTag.ACTION_COMPENSATE : SagaTraceTag.ACTION_EXECUTE;

        return sagaTraceTag.open(
                spanName,
                UUID.fromString(sagaId),
                SagaTraceTag.stepTag(AggregateTypes.STOCK_RESERVATION),
                action,
                eventId);
    }

    /**
     * 처리 결과를 스팬에 적는다.
     * <p>
     * 재고 부족은 시스템이 제대로 돈 결과이지 오류가 아니다.
     * 그래서 span error 대신 saga.result=failed와 사유 태그로만 남긴다.
     * 이미 처리한 메시지라 아무 일도 하지 않았으면 성공도 실패도 아닌 skipped로 적는다.
     */
    private void describe(SagaSpan span, StockReservationResponsePayload response) {
        if (response == null) {
            span.result(SagaTraceTag.RESULT_SKIPPED);
            return;
        }
        if (response.result() == StockResult.OUT_OF_STOCK) {
            span.failed(response.failureCode());
            return;
        }
        span.result(SagaTraceTag.RESULT_SUCCESS);
    }
}
