package org.coupon.racecoupon.service;

import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.kafka.CouponIssueProducer;
import org.coupon.racecoupon.repository.CouponRepository;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = CouponIssueProducer.TOPIC)
class KafkaCouponIssueServiceTest {

    @Autowired
    private KafkaCouponIssueService kafkaCouponIssueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long couponId;

    @BeforeEach
    void setUp() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        couponId = couponRepository.save(Coupon.create("선착순 쿠폰", 100L)).getId();
        redisTemplate.delete("coupon:" + couponId + ":count");
    }

    @Test
    @DisplayName("Kafka 비동기 발급으로 1000명이 동시에 요청해도 정확히 100개만 발급된다")
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
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(100L));
    }
}
