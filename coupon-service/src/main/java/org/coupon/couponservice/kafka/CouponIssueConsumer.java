package org.coupon.couponservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.coupon.couponservice.metrics.CouponIssueMetrics;
import org.coupon.couponservice.service.CouponIssueService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponIssueService couponIssueService;
    private final CouponIssueMetrics metrics;

    @KafkaListener(topics = CouponIssueMessage.TOPIC, groupId = "coupon-issue")
    public void consume(ConsumerRecord<String, CouponIssueMessage> record, Acknowledgment ack) {
        CouponIssueMessage message = record.value();
        log.info("발급 메시지 소비: key={}, partition={}, offset={}, couponId={}, userId={}",
                record.key(), record.partition(), record.offset(), message.couponId(), message.userId());
        try {
            metrics.recordPersistence(() ->
                    couponIssueService.persist(message.couponId(), message.userId()));
            metrics.persisted();
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            metrics.duplicateMessage();
            log.info("이미 발급된 쿠폰, 중복 메시지로 판단하고 넘어간다: couponId={}, userId={}",
                    message.couponId(), message.userId());
            ack.acknowledge();
        } catch (Exception e) {
            metrics.consumeFailed();
            log.error("발급 메시지 처리 실패: userId={}", message.userId(), e);
            throw e;
        }
    }
}
