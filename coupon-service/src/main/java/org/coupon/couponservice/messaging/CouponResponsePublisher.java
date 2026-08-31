package org.coupon.couponservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.SagaTopics;
import org.coupon.couponservice.saga.SagaTraceTag;
import org.coupon.couponservice.service.outbox.OrderOutboxHelper;
import org.coupon.couponservice.service.outbox.OrderOutboxPublishState;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponResponsePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderOutboxHelper orderOutboxHelper;
    private final OrderOutboxPublishState orderOutboxPublishState;
    private final SagaTraceTag sagaTraceTag;

    public void publish(UUID outboxId) {
        try {
            orderOutboxHelper.findById(outboxId).ifPresent(outbox -> {
                CouponResponse response = orderOutboxHelper.toResponse(outbox);

                log.info("쿠폰 응답 발행 시도: sagaId={}, orderId={}, couponStatus={}, outboxId={}",
                        response.sagaId(), response.orderId(), response.couponStatus(), outboxId);

                try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_PUBLISH, response.sagaId())) {
                    send(response, outboxId);
                } catch (Exception e) {
                    log.error("쿠폰 응답 발행 실패: sagaId={}, orderId={}, couponId={}, outboxId={}",
                            response.sagaId(), response.orderId(), response.couponId(), outboxId, e);
                }
            });
        } catch (Exception e) {
            log.error("쿠폰 응답 발행 준비 실패(row 조회 또는 payload 역직렬화): outboxId={}", outboxId, e);
        }
    }

    private void send(CouponResponse response, UUID outboxId) {
        orderOutboxPublishState.recordAttempt(outboxId);

        String key = String.valueOf(response.orderId());
        kafkaTemplate.send(SagaTopics.COUPON_RESPONSE, key, response).whenComplete((result, ex) -> {
            if (ex == null) {
                var metadata = result.getRecordMetadata();
                log.info("쿠폰 응답 발행: key={}, partition={}, offset={}, outboxId={}",
                        key, metadata.partition(), metadata.offset(), outboxId);
                orderOutboxPublishState.markPublished(outboxId);
            } else {
                log.error("쿠폰 응답 발행 실패: key={}, outboxId={}", key, outboxId, ex);
                orderOutboxPublishState.markFailed(outboxId);
            }
        });
    }
}
