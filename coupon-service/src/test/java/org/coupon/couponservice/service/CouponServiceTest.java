package org.coupon.couponservice.service;

import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.dto.IssuableCouponResponse;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponServiceTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long THIRD_USER_ID = 3L;

    @Autowired
    private CouponService couponService;

    @Autowired
    private KafkaCouponIssueService kafkaCouponIssueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @BeforeEach
    void setUp() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    /**
     * 안티 조인이 무너졌는지 잡는 테스트다. {@code userId} 조건이 {@code on}이 아니라
     * {@code where}로 내려가면 LEFT JOIN이 INNER JOIN이 되어 발급 이력이 0건인 쿠폰이 통째로 사라진다.
     */
    @Test
    @DisplayName("발급 이력이 하나도 없는 쿠폰도 목록에 나온다")
    void includes_coupon_with_no_issue_history() {
        Long couponId = saveCoupon(100L, LocalDateTime.now().plusDays(1));

        List<IssuableCouponResponse> result = couponService.findIssuable(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).couponId()).isEqualTo(couponId);
        assertThat(result.get(0).remaining()).isEqualTo(100L);
        assertThat(result.get(0).totalQuantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("남이 마지막 한 장을 가져가면 재고 소진으로 제외된다")
    void excludes_sold_out_coupon() {
        Long couponId = saveCoupon(1L, LocalDateTime.now().plusDays(1));
        persistIssue(couponId, OTHER_USER_ID);

        assertThat(couponService.findIssuable(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰은 제외된다")
    void excludes_already_issued_coupon() {
        Long couponId = saveCoupon(100L, LocalDateTime.now().plusDays(1));
        persistIssue(couponId, USER_ID);

        assertThat(couponService.findIssuable(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("이미 사용한 쿠폰도 제외된다")
    void excludes_used_coupon() {
        Long couponId = saveCoupon(100L, LocalDateTime.now().plusDays(1));
        persistIssue(couponId, USER_ID);

        IssuedCoupon issued = issuedCouponRepository.findByUserIdAndCouponId(USER_ID, couponId).orElseThrow();
        issued.use(999L, USER_ID);
        issuedCouponRepository.save(issued);

        assertThat(couponService.findIssuable(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 발급 이력은 내 목록에 영향을 주지 않는다")
    void other_users_history_does_not_affect_me() {
        Long couponId = saveCoupon(100L, LocalDateTime.now().plusDays(1));
        persistIssue(couponId, OTHER_USER_ID);

        List<IssuableCouponResponse> result = couponService.findIssuable(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).couponId()).isEqualTo(couponId);
    }

    @Test
    @DisplayName("이벤트가 끝난 쿠폰은 재고가 남아 있어도 제외된다")
    void excludes_ended_event_even_with_stock() {
        saveCoupon(100L, LocalDateTime.now().minusMinutes(1));

        assertThat(couponService.findIssuable(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("remaining은 발급된 만큼 줄어든다")
    void remaining_reflects_issued_quantity() {
        Long couponId = saveCoupon(100L, LocalDateTime.now().plusDays(1));
        persistIssue(couponId, OTHER_USER_ID);
        persistIssue(couponId, THIRD_USER_ID);

        List<IssuableCouponResponse> result = couponService.findIssuable(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).remaining()).isEqualTo(98L);
    }

    /** 발급 컨슈머와 같은 경로다 — IssuedCoupon 저장과 issuedQuantity 증가가 한 트랜잭션이다. */
    private void persistIssue(Long couponId, Long userId) {
        kafkaCouponIssueService.persist(couponId, userId);
    }

    private Long saveCoupon(Long totalQuantity, LocalDateTime eventEndAt) {
        return couponRepository.save(Coupon.builder()
                .title("선착순 쿠폰")
                .totalQuantity(totalQuantity)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .eventEndAt(eventEndAt)
                .build()).getId();
    }
}
