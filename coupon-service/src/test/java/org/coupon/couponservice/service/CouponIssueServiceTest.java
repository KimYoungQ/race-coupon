package org.coupon.couponservice.service;

import org.coupon.common.exception.BusinessException;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.dto.CouponIssueAcceptedResponse;
import org.coupon.couponservice.exception.CouponAlreadyIssuedException;
import org.coupon.couponservice.kafka.CouponIssueProducer;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.coupon.couponservice.support.MySqlTestContainer;
import org.coupon.couponservice.support.RedisTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({MySqlTestContainer.class, RedisTestContainer.class})
class CouponIssueServiceTest {

    private static final long TOTAL_QUANTITY = 20L;

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 발급 메시지 발행은 Kafka 없이 검증하기 위해 Mock 으로 대체한다
    @MockitoBean
    private CouponIssueProducer couponIssueProducer;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        redisTemplate.delete(redisTemplate.keys("coupon:*"));
    }

    @Test
    @DisplayName("수량이 남아 있으면 쿠폰이 발급된다")
    void issueWhenQuantityRemains() {
        // given
        Long couponId = saveCoupon();

        // when
        CouponIssueAcceptedResponse response = couponIssueService.issue(couponId, 1L);

        // then
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(issuedCount(couponId)).isEqualTo("1");
    }

    @Test
    @DisplayName("동시에 요청해도 총 발급 수는 쿠폰 한도를 넘지 않는다")
    void issueNeverExceedsTotalQuantity() throws InterruptedException {
        // given
        Long couponId = saveCoupon();
        int requestCount = 50;
        AtomicInteger issued = new AtomicInteger();

        // when
        runConcurrently(requestCount, userId -> {
            try {
                couponIssueService.issue(couponId, userId);
                issued.incrementAndGet();
            } catch (BusinessException ignored) {
                // 매진 거절
            }
        });

        // then
        assertThat(issued.get()).isEqualTo((int) TOTAL_QUANTITY);
        assertThat(issuedCount(couponId)).isEqualTo(String.valueOf(TOTAL_QUANTITY));
    }

    @Test
    @DisplayName("같은 사용자가 동시에 요청해도 한 번만 발급된다")
    void sameUserIsIssuedOnlyOnce() throws InterruptedException {
        // given
        Long couponId = saveCoupon();
        long userId = 7L;

        // when
        runConcurrently(10, ignored -> couponIssueService.issue(couponId, userId));

        // then
        assertThat(issuedCount(couponId)).isEqualTo("1");
        assertThat(redisTemplate.opsForSet().size("coupon:" + couponId + ":issued")).isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 발급받은 사용자가 다시 요청하면 거절되고 수량도 줄지 않는다")
    void alreadyIssuedUserIsRejected() {
        // given
        Long couponId = saveCoupon();
        long userId = 1L;
        couponIssueService.issue(couponId, userId);
        couponIssueService.persist(couponId, userId);

        // when & then
        assertThatThrownBy(() -> couponIssueService.issue(couponId, userId))
                .isInstanceOf(CouponAlreadyIssuedException.class);
        assertThat(issuedCount(couponId)).isEqualTo("1");
    }

    private Long saveCoupon() {
        return couponRepository.save(Coupon.builder()
                .title("선착순 쿠폰")
                .totalQuantity(TOTAL_QUANTITY)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .eventEndAt(LocalDateTime.now().plusDays(1))
                .build()).getId();
    }

    private String issuedCount(Long couponId) {
        return redisTemplate.opsForValue().get("coupon:" + couponId + ":seq");
    }

    private void runConcurrently(int count, LongConsumer request) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    request.accept(userId);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
    }
}
