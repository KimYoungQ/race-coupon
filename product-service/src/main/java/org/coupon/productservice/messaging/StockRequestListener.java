package org.coupon.productservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.productservice.saga.SagaTraceTag;
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
        try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, UUID.fromString(sagaId))) {
            stockRequestHandler.handle(sagaId, eventIdText, body);
        }
    }
}
