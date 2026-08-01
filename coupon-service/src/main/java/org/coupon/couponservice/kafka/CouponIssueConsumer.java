package org.coupon.couponservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.metrics.CouponIssueMetrics;
import org.coupon.couponservice.service.KafkaCouponIssueService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 선착순 발급 메시지를 소비해 {@link IssuedCoupon}으로 영속화한다.
 *
 * <p>이 리스너는 <b>단건 + 수동 ack</b>다. Saga 리스너들이 batch + 컨테이너 커밋을 쓰는 것과 다른데,
 * 성격이 다르기 때문이다 — 여기서 저장에 실패하면 "발급 자체가 없었던 일"이 되므로 재시도로 살려야 하고,
 * Saga 리스너의 중복 응답은 "이미 처리됨"이라 재시도할 이유가 없다.
 *
 * <p>저장은 {@code KafkaCouponIssueService#persist}에 맡기고 예외는 여기서 잡는다.
 * 저장과 수량 증가가 한 트랜잭션이어야 하는데, 트랜잭션 안에서 제약 위반을 잡으면
 * 이미 롤백 표시된 트랜잭션을 이어 갈 수 없기 때문이다. 경계 밖에서 잡아야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final KafkaCouponIssueService kafkaCouponIssueService;
    private final CouponIssueMetrics metrics;

    @KafkaListener(topics = CouponIssueMessage.TOPIC, groupId = "coupon-issue")
    public void consume(ConsumerRecord<String, CouponIssueMessage> record, Acknowledgment ack) {
        CouponIssueMessage message = record.value();
        log.info("발급 메시지 소비: key={}, partition={}, offset={}, couponId={}, userId={}",
                record.key(), record.partition(), record.offset(), message.couponId(), message.userId());
        try {
            metrics.recordPersistence(() ->
                    kafkaCouponIssueService.persist(message.couponId(), message.userId()));
            metrics.persisted();
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(user_id, coupon_id) 위반 = 이 메시지를 이미 처리했다는 뜻이다.
            // Kafka는 at-least-once라 저장 성공 후 ack 전에 중단되면 같은 메시지를 다시 받는다.
            // 재시도해도 결과가 같으므로 오프셋을 진행시킨다. 삼키지 않으면 무한 재시도 루프가 된다.
            metrics.duplicateMessage();
            log.info("이미 발급된 쿠폰, 중복 메시지로 판단하고 넘어간다: couponId={}, userId={}",
                    message.couponId(), message.userId());
            ack.acknowledge();
        } catch (Exception e) {
            // 예상하지 못한 DB 장애는 "아직 처리 못 함"이다. 삼키면 발급이 영구 유실된다.
            metrics.consumeFailed();
            log.error("발급 메시지 처리 실패: userId={}", message.userId(), e);
            throw e;
        }
    }
}
