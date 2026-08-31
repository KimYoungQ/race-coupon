package org.coupon.productservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockResponse;
import org.coupon.productservice.saga.SagaTraceTag;
import org.coupon.productservice.service.outbox.OrderOutboxHelper;
import org.coupon.productservice.service.outbox.OrderOutboxPublishState;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockResponsePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderOutboxHelper orderOutboxHelper;
    private final OrderOutboxPublishState orderOutboxPublishState;
    private final SagaTraceTag sagaTraceTag;

    public void publish(UUID outboxId) {
        try {
            orderOutboxHelper.findById(outboxId).ifPresent(outbox -> {
                StockResponse response = orderOutboxHelper.toResponse(outbox);

                log.info("재고 응답 발행 시도: sagaId={}, orderId={}, stockStatus={}, outboxId={}",
                        response.sagaId(), response.orderId(), response.stockStatus(), outboxId);

                try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_PUBLISH, response.sagaId())) {
                    send(response, outboxId);
                } catch (Exception e) {
                    log.error("재고 응답 발행 실패: sagaId={}, orderId={}, outboxId={}",
                            response.sagaId(), response.orderId(), outboxId, e);
                }
            });
        } catch (Exception e) {
            log.error("재고 응답 발행 준비 실패(row 조회 또는 payload 역직렬화): outboxId={}", outboxId, e);
        }
    }

    private void send(StockResponse response, UUID outboxId) {
        orderOutboxPublishState.recordAttempt(outboxId);

        String key = String.valueOf(response.orderId());
        kafkaTemplate.send(SagaTopics.PRODUCT_RESPONSE, key, response).whenComplete((result, ex) -> {
            if (ex == null) {
                var metadata = result.getRecordMetadata();
                log.info("재고 응답 발행: key={}, partition={}, offset={}, outboxId={}",
                        key, metadata.partition(), metadata.offset(), outboxId);
                orderOutboxPublishState.markPublished(outboxId);
            } else {
                log.error("재고 응답 발행 실패: key={}, outboxId={}", key, outboxId, ex);
                orderOutboxPublishState.markFailed(outboxId);
            }
        });
    }
}
