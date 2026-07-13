package org.coupon.racecoupon.kafka;

import org.coupon.racecoupon.repository.IssuedCouponRepository;
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
@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.datasource.url=jdbc:h2:mem:coupon_consumer;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.sql.init.mode=never"
})
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
        long couponId = 1L;
        long userId = 42L;

        // when
        kafkaTemplate.send(CouponIssueMessage.TOPIC, String.valueOf(userId), new CouponIssueMessage(couponId, userId));

        // then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L));
    }
}
