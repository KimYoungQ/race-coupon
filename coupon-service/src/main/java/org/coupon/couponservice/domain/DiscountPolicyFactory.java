package org.coupon.couponservice.domain;

/**
 * 쿠폰으로부터 할인 정책을 조립한다.
 * 할인 유형({@link DiscountType})으로 기본 전략을 고르고, 쿠폰에 설정된 조건을 데코레이터로 겹쳐 감싼다.
 */
public class DiscountPolicyFactory {

    private DiscountPolicyFactory() {
    }

    public static DiscountPolicy create(Coupon coupon) {
        DiscountPolicy policy = basePolicy(coupon);

        if (coupon.getMaxDiscountAmount() != null) {
            policy = new MaxDiscountDecorator(policy, coupon.getMaxDiscountAmount());
        }

        if (coupon.getMinOrderAmount() != null) {
            policy = new MinOrderAmountDecorator(policy, coupon.getMinOrderAmount());
        }

        return policy;
    }

    private static DiscountPolicy basePolicy(Coupon coupon) {
        return switch (coupon.getDiscountType()) {
            case PERCENT -> new RateDiscountPolicy(coupon.getDiscountValue());
            case FIXED_AMOUNT -> new FixDiscountPolicy(coupon.getDiscountValue());
        };
    }
}
