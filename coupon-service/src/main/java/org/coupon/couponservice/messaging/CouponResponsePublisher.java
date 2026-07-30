package org.coupon.couponservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.SagaTopics;
import org.coupon.couponservice.service.outbox.OrderOutboxHelper;
import org.coupon.couponservice.service.outbox.OrderOutboxPublishState;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 쿠폰 처리 결과를 order-service로 내보낸다. 진실의 원천은 Outbox row이고 여기는 실어 나르기만 한다.
 *
 * <p>호출자는 스케줄러뿐이다. 도메인 처리 중에 직접 발행하면 커밋 전에 나가버리거나
 * (롤백된 결과가 이미 전달됨) 커밋 직후 프로세스가 죽어 영영 안 나간다.
 *
 * <p><b>{@code KafkaTemplate} 빈을 새로 선언하지 않는다.</b> Boot의 {@code KafkaAutoConfiguration}이
 * 이 타입을 {@code @ConditionalOnMissingBean(KafkaTemplate.class)}으로 정의하므로,
 * 전용 템플릿을 올리면 기본 빈이 사라져 <b>선착순 발급 프로듀서가 함께 깨진다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponResponsePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderOutboxHelper orderOutboxHelper;
    private final OrderOutboxPublishState orderOutboxPublishState;

    /**
     * 한 건을 발행한다. <b>예외를 밖으로 내보내지 않는다.</b>
     *
     * <p>스케줄러가 배치를 {@code forEach}로 돌기 때문에, 여기서 예외가 새면 뒤의 row들이
     * 이번 틱에 나가지 못한다. payload가 깨진 row처럼 매번 같은 자리에서 실패하는 건이 있으면
     * 그 뒤에 줄 선 응답은 영원히 못 나간다.
     *
     * <p>삼켜도 유실되지 않는다. row는 성공 콜백에서만 COMPLETED가 되므로 STARTED로 남아 다시 집힌다.
     */
    public void publish(UUID outboxId) {
        try {
            orderOutboxHelper.findById(outboxId).ifPresent(outbox -> {
                CouponResponse response = orderOutboxHelper.toResponse(outbox);

                log.info("쿠폰 응답 발행 시도: sagaId={}, orderId={}, couponStatus={}, outboxId={}",
                        response.sagaId(), response.orderId(), response.couponStatus(), outboxId);

                try {
                    send(response, outboxId);
                } catch (Exception e) {
                    log.error("쿠폰 응답 발행 실패: sagaId={}, orderId={}, couponId={}, outboxId={}",
                            response.sagaId(), response.orderId(), response.couponId(), outboxId, e);
                }
            });
        } catch (Exception e) {
            // 여기까지 온 것은 row 조회나 payload 역직렬화가 깨진 경우다.
            // 사가를 특정할 값이 없어 outboxId만 남길 수 있다.
            log.error("쿠폰 응답 발행 준비 실패(row 조회 또는 payload 역직렬화): outboxId={}", outboxId, e);
        }
    }

    /**
     * 응답의 파티션 키는 {@code orderId}다 — 같은 주문에 대한 결과가 order-service에서
     * 순차 처리되도록 한다. 요청 쪽 키({@code userId:couponId})와 목적이 다르다.
     */
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
