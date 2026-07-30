package org.coupon.couponservice.kafka;

import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * coupon-consumer의 책임: 토픽에 올라온 발급 메시지를 소비해 IssuedCoupon으로 영속화한다.
 * 실제 인프라 없이 검증하도록 임베디드 Kafka + 인메모리 H2를 사용한다.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = CouponIssueMessage.TOPIC)
class CouponIssueConsumerTest {

    @Autowired
    private KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @BeforeEach
    void setUp() {
        issuedCouponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("토픽에 올라온 발급 메시지를 소비해 IssuedCoupon으로 저장한다")
    void consume_and_persist() {
        // given
        long couponId = 999_999L;
        long userId = 42L;

        // when
        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(userId), new CouponIssueMessage(couponId, userId));

        // then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L));
    }

    /**
     * Kafka는 at-least-once다. 저장에 성공한 뒤 ack 전에 컨슈머가 멈추면 같은 메시지를 다시 받는다.
     * 그때 발급이 두 장 생기지 않도록 막는 것은 {@code UNIQUE(user_id, coupon_id)}이고,
     * 컨슈머는 그 위반을 "이미 처리됨"으로 읽어 오프셋을 진행시켜야 한다.
     * 삼키지 않으면 같은 메시지에서 무한히 재시도한다.
     */
    @Test
    @DisplayName("같은 발급 메시지를 두 번 받아도 IssuedCoupon은 한 건만 남는다")
    void duplicate_message_is_idempotent() {
        // given
        long couponId = 999_998L;
        long userId = 43L;
        CouponIssueMessage message = new CouponIssueMessage(couponId, userId);

        // when — 같은 키라 같은 파티션에서 순서대로 처리된다
        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(userId), message);
        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(userId), message);

        // then — 1건이 되고, 그 뒤로도 계속 1건이어야 한다(두 번째 메시지가 나중에 반영되지 않음)
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L));
    }
}
