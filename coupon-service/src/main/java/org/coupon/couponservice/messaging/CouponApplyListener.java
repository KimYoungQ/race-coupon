package org.coupon.couponservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.couponservice.saga.SagaTraceTag;
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
        try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, UUID.fromString(sagaId))) {
            couponApplyHandler.handle(sagaId, eventIdText, body);
        }
    }
}
