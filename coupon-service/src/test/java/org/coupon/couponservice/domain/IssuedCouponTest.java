package org.coupon.couponservice.domain;

import org.coupon.couponservice.exception.CouponAlreadyUsedException;
import org.coupon.couponservice.exception.CouponNotOwnedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 발급 건의 사용·복구 계약. 사가의 마지막 단계가 이 도메인 위에서 돈다.
 */
class IssuedCouponTest {

    private static final long USER_ID = 42L;
    private static final long COUPON_ID = 9L;
    private static final long ORDER_ID = 100L;

    private IssuedCoupon issued() {
        return IssuedCoupon.builder().userId(USER_ID).couponId(COUPON_ID).build();
    }

    @Test
    @DisplayName("발급 직후에는 ISSUED이고 어느 주문에도 묶여 있지 않다")
    void starts_as_issued() {
        IssuedCoupon coupon = issued();

        assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(coupon.getOrderId()).isNull();
        assertThat(coupon.getUsedAt()).isNull();
    }

    @Nested
    @DisplayName("use()")
    class Use {

        @Test
        @DisplayName("주문에 사용하면 USED가 되고 사용 주문과 시각이 남는다")
        void marks_used() {
            IssuedCoupon coupon = issued();

            coupon.use(ORDER_ID, USER_ID);

            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(coupon.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(coupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 사용된 쿠폰을 다시 쓰려 하면 거부한다 — 중복 요청의 최종 방어선")
        void rejects_second_use() {
            IssuedCoupon coupon = issued();
            coupon.use(ORDER_ID, USER_ID);

            assertThatThrownBy(() -> coupon.use(ORDER_ID, USER_ID))
                    .isInstanceOf(CouponAlreadyUsedException.class);
        }

        @Test
        @DisplayName("같은 요청이 두 번 와도 사용 주문이 바뀌지 않는다")
        void second_use_does_not_rebind() {
            IssuedCoupon coupon = issued();
            coupon.use(ORDER_ID, USER_ID);

            assertThatThrownBy(() -> coupon.use(999L, USER_ID))
                    .isInstanceOf(CouponAlreadyUsedException.class);
            assertThat(coupon.getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("다른 사용자의 쿠폰은 쓸 수 없다")
        void rejects_other_owner() {
            IssuedCoupon coupon = issued();

            assertThatThrownBy(() -> coupon.use(ORDER_ID, USER_ID + 1))
                    .isInstanceOf(CouponNotOwnedException.class);
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        }
    }

    @Nested
    @DisplayName("restore()")
    class Restore {

        @Test
        @DisplayName("사용된 쿠폰을 되돌리면 다시 쓸 수 있는 상태가 된다")
        void restores() {
            IssuedCoupon coupon = issued();
            coupon.use(ORDER_ID, USER_ID);

            assertThat(coupon.restore(ORDER_ID)).isTrue();
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
            assertThat(coupon.getOrderId()).isNull();
            assertThat(coupon.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("이미 되돌린 쿠폰은 false를 반환해 호출부가 두 번 처리하지 않게 한다")
        void second_restore_returns_false() {
            IssuedCoupon coupon = issued();
            coupon.use(ORDER_ID, USER_ID);
            coupon.restore(ORDER_ID);

            assertThat(coupon.restore(ORDER_ID)).isFalse();
        }

        @Test
        @DisplayName("다른 주문이 쓴 쿠폰은 되돌리지 않는다")
        void does_not_restore_other_order() {
            IssuedCoupon coupon = issued();
            coupon.use(ORDER_ID, USER_ID);

            assertThat(coupon.restore(ORDER_ID + 1)).isFalse();
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(coupon.getOrderId()).isEqualTo(ORDER_ID);
        }
    }

    @Test
    @DisplayName("isUsedBy는 그 주문이 실제로 사용한 경우에만 참이다")
    void is_used_by() {
        IssuedCoupon coupon = issued();
        assertThat(coupon.isUsedBy(ORDER_ID)).isFalse();

        coupon.use(ORDER_ID, USER_ID);

        assertThat(coupon.isUsedBy(ORDER_ID)).isTrue();
        assertThat(coupon.isUsedBy(ORDER_ID + 1)).isFalse();
    }
}
