package org.coupon.racecoupon.service;

import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.repository.CouponRepository;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisCouponIssueServiceTest {

    @Autowired
    private RedisCouponIssueService redisCouponIssueService;

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
    @DisplayName("Redis INCR로 1000명이 동시에 발급해도 정확히 100개만 발급된다")
    void issue_coupon_with_redis() throws InterruptedException {
        // given
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    redisCouponIssueService.issue(couponId, userId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then
        long issuedCount = issuedCouponRepository.countByCouponId(couponId);
        assertThat(issuedCount).isEqualTo(100L);
    }

    @Test
    @DisplayName("발급 시 카운트 키에 TTL(하루)이 설정된다")
    void count_key_has_ttl() {
        // when
        redisCouponIssueService.issue(couponId, 1L);

        // then
        Long ttl = redisTemplate.getExpire("coupon:" + couponId + ":count");
        assertThat(ttl).isBetween(1L, 86400L);
    }
}
