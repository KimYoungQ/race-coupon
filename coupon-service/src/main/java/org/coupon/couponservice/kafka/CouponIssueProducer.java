package org.coupon.couponservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.couponservice.metrics.CouponIssueMetrics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueProducer {

    private final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;
    private final CouponIssueMetrics metrics;

    /**
     * 계측은 {@code send()} 직후가 아니라 콜백에서 한다. 전송은 비동기라
     * 반환 시점에는 브로커가 받았는지 알 수 없다.
     *
     * <p>실패 카운터는 <b>Redis가 발급을 이미 확정한 뒤에</b> 메시지가 나가지 못한 수다.
     * 이 값이 오르면 해당 사용자는 스스로 재요청해 재발행 경로를 타야만 쿠폰을 받는다 —
     * 자동 복구는 없다.
     */
    public void issue(CouponIssueMessage message) {
        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(message.userId()), message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        metrics.messageProduced();
                        var metadata = result.getRecordMetadata();
                        log.info("발급 메시지 전송 성공: userId={}, partition={}, offset={}",
                                message.userId(), metadata.partition(), metadata.offset());
                    } else {
                        metrics.messageProduceFailed();
                        log.error("발급 메시지 전송 실패: userId={}", message.userId(), ex);
                    }
                });
    }
}
