package org.coupon.couponservice.domain;

import org.coupon.couponservice.exception.CouponAlreadyUsedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuedCouponTest {

    private static final long USER_ID = 42L;
    private static final long COUPON_ID = 9L;
    private static final long ORDER_ID = 100L;

    @Test
    @DisplayName("발급된 쿠폰은 주문에 사용할 수 있다")
    void useIssuedCoupon() {
        // given
        IssuedCoupon coupon = issuedCoupon();

        // when
        coupon.use(ORDER_ID, USER_ID);

        // then
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(coupon.getOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("사용한 쿠폰은 다시 사용할 수 없다")
    void cannotUseTwice() {
        // given
        IssuedCoupon coupon = issuedCoupon();
        coupon.use(ORDER_ID, USER_ID);

        // when & then
        assertThatThrownBy(() -> coupon.use(999L, USER_ID))
                .isInstanceOf(CouponAlreadyUsedException.class);
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(coupon.getOrderId()).isEqualTo(ORDER_ID);
    }

    private IssuedCoupon issuedCoupon() {
        return IssuedCoupon.builder()
                .userId(USER_ID)
                .couponId(COUPON_ID)
                .build();
    }
}
