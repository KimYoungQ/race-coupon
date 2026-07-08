package org.coupon.racecoupon.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueProducer {

    public static final String TOPIC = "coupon-issue";

    private final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    public void issue(CouponIssueMessage message) {
        kafkaTemplate.send(TOPIC, String.valueOf(message.userId()), message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        var metadata = result.getRecordMetadata();
                        log.info("발급 메시지 전송 성공: userId={}, partition={}, offset={}",
                                message.userId(), metadata.partition(), metadata.offset());
                    } else {
                        log.error("발급 메시지 전송 실패: userId={}", message.userId(), ex);
                    }
                });
    }
}
