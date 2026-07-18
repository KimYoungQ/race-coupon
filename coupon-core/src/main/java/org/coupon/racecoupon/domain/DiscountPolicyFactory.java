package org.coupon.racecoupon.domain;

/**
 * 쿠폰의 할인 유형({@link DiscountType})을 보고 알맞은 {@link DiscountPolicy} 전략을 생성한다.
 */
public class DiscountPolicyFactory {

    private DiscountPolicyFactory() {
    }

    public static DiscountPolicy create(Coupon coupon) {
        return switch (coupon.getDiscountType()) {
            case PERCENT -> new RateDiscountPolicy(coupon.getDiscountValue());
            case FIXED_AMOUNT -> new FixDiscountPolicy(coupon.getDiscountValue());
        };
    }
}
