package org.coupon.couponservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssuedCouponTest {

    private static final long USER_ID = 42L;
    private static final long COUPON_ID = 9L;

    @Test
    @DisplayName("발급된 쿠폰은 사용 가능한 상태로 시작한다")
    void startsAsIssued() {
        // when
        IssuedCoupon coupon = IssuedCoupon.builder()
                .userId(USER_ID)
                .couponId(COUPON_ID)
                .build();

        // then
        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(coupon.getOrderId()).isNull();
        assertThat(coupon.getUsedAt()).isNull();
    }
}
