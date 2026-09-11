package org.coupon.productservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.productservice.service.StockSagaService;
import org.coupon.sagapersistence.idempotency.MessageLog;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRequestHandler {

    private final MessageLog messageLog;
    private final StockSagaService stockSagaService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 재고 요청 한 건을 처리한다.
     * <p>
     * 본문 해석은 리스너가 먼저 한다. 실행(REQUEST)이냐 보상(CANCEL)이냐에 따라
     * 추적 스팬 이름이 갈리는데, 스팬은 트랜잭션 바깥에서 열려야 응답 outbox.write까지 품는다.
     * 그래서 이 메서드는 이미 해석된 요청을 받는다.
     *
     * @return 이번에 만든 응답. 이미 처리한 메시지라 건너뛰었으면 {@code null}
     */
    @Transactional
    public StockReservationResponsePayload handle(String sagaId, String eventId,
                                                  StockReservationRequestPayload request) {
        if (messageLog.alreadyProcessed(eventId)) {
            log.info("이미 처리한 재고 요청, 건너뛴다: sagaId={}, eventId={}", sagaId, eventId);
            return null;
        }

        StockReservationResponsePayload response = switch (request.type()) {
            case REQUEST -> stockSagaService.reserve(request);
            case CANCEL -> stockSagaService.release(request);
        };

        eventPublisher.publishEvent(new StockResponded(sagaId, response));
        messageLog.markProcessed(eventId);

        log.info("재고 요청 처리: sagaId={}, eventId={}, type={}, orderId={}, result={}, failureCode={}",
                sagaId, eventId, request.type(), request.orderId(), response.result(), response.failureCode());
        return response;
    }
}
