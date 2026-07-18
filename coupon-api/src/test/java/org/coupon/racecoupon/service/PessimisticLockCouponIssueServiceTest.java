package org.coupon.racecoupon.service;

import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.domain.DiscountType;
import org.coupon.racecoupon.repository.CouponRepository;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PessimisticLockCouponIssueServiceTest {

    @Autowired
    private PessimisticLockCouponIssueService pessimisticLockCouponIssueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    private Long couponId;

    @BeforeEach
    void setUp() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        couponId = couponRepository.save(Coupon.create("선착순 쿠폰", 100L, DiscountType.PERCENT, 10L)).getId();
    }

    @Test
    @DisplayName("비관적 락으로 1000명이 동시에 발급해도 정확히 100개만 발급된다")
    void issue_coupon_with_pessimistic_lock() throws InterruptedException {
        // given
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    pessimisticLockCouponIssueService.issue(couponId, userId);
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
}
