package org.coupon.couponservice.kafka;

import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.coupon.couponservice.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = CouponIssueMessage.TOPIC)
@Import(MySqlTestContainer.class)
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
        long couponId = 999_999L;
        long userId = 42L;

        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(userId), new CouponIssueMessage(couponId, userId));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L));
    }

    @Test
    @DisplayName("같은 발급 메시지를 두 번 받아도 IssuedCoupon은 한 건만 남는다")
    void duplicate_message_is_idempotent() {
        long couponId = 999_998L;
        long userId = 43L;
        CouponIssueMessage message = new CouponIssueMessage(couponId, userId);

        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(userId), message);
        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(userId), message);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L));
    }
}
