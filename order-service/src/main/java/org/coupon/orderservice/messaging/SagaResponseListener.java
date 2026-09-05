package org.coupon.orderservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.coupon.sagapersistence.tracing.SagaTraceTag;
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
        try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, UUID.fromString(sagaId))) {
            sagaResponseHandler.onStockResponse(sagaId, eventIdValue, body);
        } catch (InvalidOrderStateException e) {
            log.error("주문 상태와 사가 상태가 어긋난 재고 응답, 건너뜀: sagaId={}, eventId={}", sagaId, eventIdValue, e);
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
        try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, UUID.fromString(sagaId))) {
            sagaResponseHandler.onCouponResponse(sagaId, eventIdValue, body);
        } catch (InvalidOrderStateException e) {
            log.error("주문 상태와 사가 상태가 어긋난 쿠폰 응답, 건너뜀: sagaId={}, eventId={}", sagaId, eventIdValue, e);
        }
    }
}
