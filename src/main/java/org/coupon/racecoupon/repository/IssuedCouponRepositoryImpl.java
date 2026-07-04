package org.coupon.racecoupon.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import static org.coupon.racecoupon.domain.QIssuedCoupon.issuedCoupon;

@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public long countByCouponId(Long couponId) {
        Long count = queryFactory
                .select(issuedCoupon.count())
                .from(issuedCoupon)
                .where(issuedCoupon.couponId.eq(couponId))
                .fetchOne();
        return count != null ? count : 0L;
    }
}
