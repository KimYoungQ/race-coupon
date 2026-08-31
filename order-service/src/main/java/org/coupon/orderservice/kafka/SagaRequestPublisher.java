package org.coupon.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockRequest;
import org.coupon.orderservice.saga.SagaChannel;
import org.coupon.orderservice.saga.SagaTraceTag;
import org.coupon.orderservice.service.outbox.CouponOutboxHelper;
import org.coupon.orderservice.service.outbox.OutboxPublishState;
import org.coupon.orderservice.service.outbox.ProductOutboxHelper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRequestPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProductOutboxHelper productOutboxHelper;
    private final CouponOutboxHelper couponOutboxHelper;
    private final OutboxPublishState outboxPublishState;
    private final SagaTraceTag sagaTraceTag;

    public void publish(UUID outboxId, SagaChannel channel) {
        try {
            switch (channel) {
                case PRODUCT -> publishProduct(outboxId);
                case COUPON -> publishCoupon(outboxId);
            }
        } catch (Exception e) {
            log.error("Saga 요청 발행 준비 실패(row 조회 또는 payload 역직렬화): outboxId={}, channel={}",
                    outboxId, channel, e);
        }
    }

    private void publishProduct(UUID outboxId) {
        productOutboxHelper.findById(outboxId).ifPresent(outbox -> {
            StockRequest request = productOutboxHelper.toRequest(outbox);

            log.info("재고 요청 발행 시도: sagaId={}, orderId={}, requestStatus={}, outboxId={}",
                    request.sagaId(), request.orderId(), request.stockOrderStatus(), outboxId);

            try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_PUBLISH, request.sagaId())) {
                send(SagaTopics.PRODUCT_REQUEST, stockKey(request), request, outboxId, SagaChannel.PRODUCT);
            } catch (Exception e) {
                log.error("재고 요청 발행 실패: sagaId={}, orderId={}, productId={}, outboxId={}",
                        request.sagaId(), request.orderId(), request.productId(), outboxId, e);
            }
        });
    }

    private void publishCoupon(UUID outboxId) {
        couponOutboxHelper.findById(outboxId).ifPresent(outbox -> {
            CouponRequest request = couponOutboxHelper.toRequest(outbox);

            log.info("쿠폰 요청 발행 시도: sagaId={}, orderId={}, requestStatus={}, outboxId={}",
                    request.sagaId(), request.orderId(), request.couponOrderStatus(), outboxId);

            try (var ignored = sagaTraceTag.open(SagaTraceTag.OUTBOX_PUBLISH, request.sagaId())) {
                send(SagaTopics.COUPON_REQUEST, couponKey(request), request, outboxId, SagaChannel.COUPON);
            } catch (Exception e) {
                log.error("쿠폰 요청 발행 실패: sagaId={}, orderId={}, couponId={}, outboxId={}",
                        request.sagaId(), request.orderId(), request.couponId(), outboxId, e);
            }
        });
    }

    private String stockKey(StockRequest request) {
        return String.valueOf(request.productId());
    }

    private String couponKey(CouponRequest request) {
        return request.userId() + ":" + request.couponId();
    }

    private void send(String topic, String key, Object payload, UUID outboxId, SagaChannel channel) {
        outboxPublishState.recordAttempt(outboxId, channel);

        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex == null) {
                var metadata = result.getRecordMetadata();
                log.info("Saga 요청 발행: topic={}, key={}, partition={}, offset={}, outboxId={}",
                        topic, key, metadata.partition(), metadata.offset(), outboxId);
                outboxPublishState.markPublished(outboxId, channel);
            } else {
                log.error("Saga 요청 발행 실패: topic={}, key={}, outboxId={}", topic, key, outboxId, ex);
                outboxPublishState.markFailed(outboxId, channel);
            }
        });
    }
}
