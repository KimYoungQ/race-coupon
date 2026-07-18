package org.coupon.racecoupon.domain;

/**
 * 정액 할인 전략. 원가와 무관하게 {@code amount}원을 할인액으로 계산한다.
 * 원가를 초과하는 경우의 0 하한은 {@link Coupon#finalPrice(long)}가 흡수한다.
 */
public class FixDiscountPolicy implements DiscountPolicy {

    private final long amount;

    public FixDiscountPolicy(long amount) {
        this.amount = amount;
    }

    @Override
    public long discount(long price) {
        return amount;
    }
}
