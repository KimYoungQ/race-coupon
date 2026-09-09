package org.coupon.couponservice.service;

import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.coupon.couponservice.support.MySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Import(MySqlTestContainer.class)
class CouponServiceTest {

    private static final long USER_ID = 42L;
    private static final long OTHER_USER_ID = 43L;
    private static final long ORDER_ID = 100L;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("내가 발급받고 아직 쓰지 않은 쿠폰이면 예외 없이 통과한다")
    void usableCoupon() {
        // given
        Long couponId = saveCoupon();
        issueCoupon(couponId, USER_ID);

        // when & then
        assertThatCode(() -> couponService.checkUsable(USER_ID, couponId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("내 발급 이력이 없는 쿠폰이면 COUPON_NOT_ISSUED_YET 예외가 난다")
    void couponNotIssuedYet() {
        // given
        Long couponId = saveCoupon();
        issueCoupon(couponId, OTHER_USER_ID);

        // when
        Throwable thrown = catchThrowable(() -> couponService.checkUsable(USER_ID, couponId));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_ISSUED_YET);
    }

    @Test
    @Transactional // markUsed 는 벌크 update 라 트랜잭션 안에서만 실행된다
    @DisplayName("이미 사용한 쿠폰이면 COUPON_ALREADY_USED 예외가 난다")
    void couponAlreadyUsed() {
        // given
        Long couponId = saveCoupon();
        issueCoupon(couponId, USER_ID);
        issuedCouponRepository.markUsed(USER_ID, couponId, ORDER_ID, LocalDateTime.now());

        // when
        Throwable thrown = catchThrowable(() -> couponService.checkUsable(USER_ID, couponId));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COUPON_ALREADY_USED);
    }

    private Long saveCoupon() {
        return couponRepository.save(Coupon.builder()
                .title("10% 할인")
                .totalQuantity(100L)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .eventEndAt(LocalDateTime.now().plusDays(1))
                .build()).getId();
    }

    private void issueCoupon(Long couponId, Long userId) {
        issuedCouponRepository.save(IssuedCoupon.builder().userId(userId).couponId(couponId).build());
    }
}
