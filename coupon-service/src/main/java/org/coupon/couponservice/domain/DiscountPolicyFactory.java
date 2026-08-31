package org.coupon.couponservice.domain;

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
