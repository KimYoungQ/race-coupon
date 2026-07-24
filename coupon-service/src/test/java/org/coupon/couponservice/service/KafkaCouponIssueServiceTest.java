package org.coupon.couponservice.service;

import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.kafka.CouponIssueMessage;
import org.coupon.couponservice.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * coupon-api는 Redis 카운팅으로 발급 수량을 통제하고 Kafka로 메시지를 넘기는 것까지가 책임이다.
 * 실제 DB 영속화는 coupon-consumer의 책임이므로 여기서는 Redis 카운트만 검증한다.
 * (영속화 검증은 coupon-consumer의 CouponIssueConsumerTest)
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = CouponIssueMessage.TOPIC)
class KafkaCouponIssueServiceTest {

    @Autowired
    private KafkaCouponIssueService kafkaCouponIssueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long couponId;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAllInBatch();
        couponId = couponRepository.save(Coupon.create("선착순 쿠폰", 100L, DiscountType.PERCENT, 10L)).getId();
        redisTemplate.delete("coupon:" + couponId + ":count");
    }

    @Test
    @DisplayName("Kafka 전략에서 1000명이 동시에 요청해도 Redis 카운트가 정확히 100에서 멈춘다")
    void issue_coupon_with_kafka() throws InterruptedException {
        // given
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    kafkaCouponIssueService.issue(couponId, userId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then
        String count = redisTemplate.opsForValue().get("coupon:" + couponId + ":count");
        assertThat(count).isEqualTo("100");
    }
}
