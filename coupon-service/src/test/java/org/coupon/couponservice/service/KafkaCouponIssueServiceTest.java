package org.coupon.couponservice.service;

import org.coupon.common.exception.BusinessException;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.dto.CouponIssueAcceptedResponse;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.exception.CouponAlreadyIssuedException;
import org.coupon.couponservice.exception.CouponEventEndedException;
import org.coupon.couponservice.kafka.CouponIssueMessage;
import org.coupon.couponservice.repository.CouponIssueRedisRepository;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 발급 자격 검증은 {@code issue_coupon.lua}가 원자적으로 수행한다.
 * 검증을 통과한 요청만 카운터를 올리므로 <b>seq는 시도 수가 아니라 발급 수</b>이고,
 * 그 성질이 이 테스트들의 단언 기준이다.
 *
 * <p>로컬 Redis(6379)가 필요하다: {@code docker-compose up -d redis}
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = CouponIssueMessage.TOPIC)
class KafkaCouponIssueServiceTest {

    private static final long TOTAL_QUANTITY = 100L;

    @Autowired
    private KafkaCouponIssueService kafkaCouponIssueService;

    @Autowired
    private CouponIssueRedisRepository couponIssueRedisRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long couponId;
    private String seqKey;
    private String issuedKey;

    @BeforeEach
    void setUp() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        couponId = saveCoupon(LocalDateTime.now().plusDays(1));
        seqKey = "coupon:" + couponId + ":seq";
        issuedKey = "coupon:" + couponId + ":issued";
        redisTemplate.delete(List.of(seqKey, issuedKey));
    }

    @Test
    @DisplayName("서로 다른 1000명이 동시에 요청해도 정확히 100명만 발급받는다")
    void issue_stops_exactly_at_total_quantity() throws InterruptedException {
        // given
        int threadCount = 1000;
        AtomicInteger issued = new AtomicInteger();
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    kafkaCouponIssueService.issue(couponId, userId);
                    issued.incrementAndGet();
                } catch (BusinessException ignored) {
                    // 소진(409)은 정상 결과다
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then — 세 값이 모두 100이어야 한다.
        // 선증가-후비교였다면 seq가 1000까지 올라갔을 자리다.
        assertThat(issued.get()).isEqualTo((int) TOTAL_QUANTITY);
        assertThat(redisTemplate.opsForValue().get(seqKey)).isEqualTo(String.valueOf(TOTAL_QUANTITY));
        assertThat(redisTemplate.opsForSet().size(issuedKey)).isEqualTo(TOTAL_QUANTITY);

        // 컨슈머가 따라오면 DB 쪽 두 값도 같아진다
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(TOTAL_QUANTITY);
            assertThat(findCoupon().getIssuedQuantity()).isEqualTo(TOTAL_QUANTITY);
        });

        CouponIssueResponse stock = kafkaCouponIssueService.getCouponInfo(couponId);
        assertThat(stock.couponId()).isEqualTo(couponId);
        assertThat(stock.issuedQuantity()).isEqualTo(TOTAL_QUANTITY);
        assertThat(stock.remaining()).isZero();
    }

    @Test
    @DisplayName("이미 발급받은 사용자가 다시 요청하면 거절되고 수량도 줄지 않는다")
    void duplicate_request_is_rejected_without_consuming_quantity() {
        // given — 발급 건이 실제로 남을 때까지 기다린다(그 전에는 재발행 경로다)
        long userId = 1L;
        CouponIssueAcceptedResponse response = kafkaCouponIssueService.issue(couponId, userId);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(issuedCouponRepository.findByUserIdAndCouponId(userId, couponId)).isPresent());

        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.userId()).isEqualTo(userId);

        // when & then
        assertThatThrownBy(() -> kafkaCouponIssueService.issue(couponId, userId))
                .isInstanceOf(CouponAlreadyIssuedException.class);
        assertThat(redisTemplate.opsForValue().get(seqKey)).isEqualTo("1");
    }

    /**
     * 발급 이력은 Redis에 있는데 발급 건이 없는 상태 — 메시지 발행이 실패했을 때 벌어지는 일이다.
     * 여기서 409를 주면 그 사용자는 재시도할 길이 막혀 발급이 영구 유실된다. 재발행이 맞다.
     */
    @Test
    @DisplayName("발급 이력만 있고 발급 건이 없으면 거절하지 않고 다시 발행한다")
    void reissues_when_record_exists_but_row_does_not() {
        // given
        long userId = 7L;
        redisTemplate.opsForSet().add(issuedKey, String.valueOf(userId));

        // when
        kafkaCouponIssueService.issue(couponId, userId);

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(issuedCouponRepository.findByUserIdAndCouponId(userId, couponId)).isPresent());
    }

    @Test
    @DisplayName("seq와 issued에 이벤트 종료 시각 기준의 같은 TTL이 걸린다")
    void both_keys_share_ttl_derived_from_event_end() {
        // given & when
        kafkaCouponIssueService.issue(couponId, 1L);

        // then — eventEndAt이 +1일이므로 TTL도 그만큼이다
        long expected = Duration.ofDays(1).toSeconds();
        assertThat(redisTemplate.getExpire(seqKey)).isBetween(expected - 60, expected);
        assertThat(redisTemplate.getExpire(issuedKey)).isBetween(expected - 60, expected);
    }

    @Test
    @DisplayName("이벤트가 끝난 쿠폰은 수량이 남아 있어도 발급하지 않는다")
    void ended_event_is_rejected_before_touching_redis() {
        // given
        Long endedCouponId = saveCoupon(LocalDateTime.now().minusDays(2));

        // when & then
        assertThatThrownBy(() -> kafkaCouponIssueService.issue(endedCouponId, 1L))
                .isInstanceOf(CouponEventEndedException.class);
        assertThat(redisTemplate.hasKey("coupon:" + endedCouponId + ":seq")).isFalse();
    }

    /**
     * EXPIRE에 0 이하를 넘기면 Redis는 키를 즉시 삭제한다. 스크립트의 TTL 가드가 없으면
     * 이 호출 하나로 seq가 사라지고 다음 요청부터 카운터가 1에서 다시 시작한다.
     */
    @Test
    @DisplayName("TTL이 0 이하로 들어와도 스크립트가 키를 지우지 않는다")
    void non_positive_ttl_does_not_delete_keys() {
        // given
        kafkaCouponIssueService.issue(couponId, 1L);

        // when
        long result = couponIssueRedisRepository.issue(couponId, 2L, TOTAL_QUANTITY, 0L);

        // then
        assertThat(result).isEqualTo(-2L);
        assertThat(redisTemplate.hasKey(seqKey)).isTrue();
        assertThat(redisTemplate.opsForValue().get(seqKey)).isEqualTo("1");
    }

    private Coupon findCoupon() {
        return couponRepository.findById(couponId).orElseThrow();
    }

    private Long saveCoupon(LocalDateTime eventEndAt) {
        return couponRepository.save(Coupon.builder()
                .title("선착순 쿠폰")
                .totalQuantity(TOTAL_QUANTITY)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .eventEndAt(eventEndAt)
                .build()).getId();
    }
}
