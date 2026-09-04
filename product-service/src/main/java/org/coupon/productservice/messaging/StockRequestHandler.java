package org.coupon.productservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.productservice.service.StockSagaService;
import org.coupon.sagapersistence.idempotency.MessageLog;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRequestHandler {

    private final MessageLog messageLog;
    private final SagaPayloadCodec sagaPayloadCodec;
    private final StockSagaService stockSagaService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void handle(String sagaId, String eventId, String body) {
        if (messageLog.alreadyProcessed(eventId)) {
            log.info("이미 처리한 재고 요청, 건너뛴다: sagaId={}, eventId={}", sagaId, eventId);
            return;
        }

        StockReservationRequestPayload request =
                sagaPayloadCodec.deserialize(body, StockReservationRequestPayload.class);

        StockReservationResponsePayload response = switch (request.type()) {
            case REQUEST -> stockSagaService.reserve(request);
            case CANCEL -> stockSagaService.release(request);
        };

        eventPublisher.publishEvent(new StockResponded(sagaId, response));
        messageLog.markProcessed(eventId);

        log.info("재고 요청 처리: sagaId={}, eventId={}, type={}, orderId={}, result={}, failureCode={}",
                sagaId, eventId, request.type(), request.orderId(), response.result(), response.failureCode());
    }
}
