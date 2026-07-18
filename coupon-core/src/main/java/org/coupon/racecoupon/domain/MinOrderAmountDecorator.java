package org.coupon.racecoupon.domain;

/**
 * 최소 주문 금액 데코레이터(적용 게이트). 주문금액({@code price})이 기준 미만이면 할인 0으로 단락한다.
 */
public class MinOrderAmountDecorator implements DiscountPolicy {

    private final DiscountPolicy delegate;
    private final long minOrderAmount;

    public MinOrderAmountDecorator(DiscountPolicy delegate, long minOrderAmount) {
        this.delegate = delegate;
        this.minOrderAmount = minOrderAmount;
    }

    @Override
    public long discount(long price) {
        return price < minOrderAmount ? 0 : delegate.discount(price);
    }
}
