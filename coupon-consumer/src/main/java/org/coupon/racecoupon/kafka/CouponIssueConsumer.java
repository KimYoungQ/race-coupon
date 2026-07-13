package org.coupon.racecoupon.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.coupon.racecoupon.domain.IssuedCoupon;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final IssuedCouponRepository issuedCouponRepository;

    @KafkaListener(topics = CouponIssueMessage.TOPIC, groupId = "coupon-issue")
    public void consume(ConsumerRecord<String, CouponIssueMessage> record, Acknowledgment ack) {
        CouponIssueMessage message = record.value();
        log.info("발급 메시지 소비: key={}, partition={}, offset={}, couponId={}, userId={}",
                record.key(), record.partition(), record.offset(), message.couponId(), message.userId());
        try {
            issuedCouponRepository.save(IssuedCoupon.create(message.userId(), message.couponId()));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("발급 메시지 처리 실패: userId={}", message.userId(), e);
            throw e;
        }
    }
}
