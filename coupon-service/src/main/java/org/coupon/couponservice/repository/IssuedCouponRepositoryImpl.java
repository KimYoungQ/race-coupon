package org.coupon.couponservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static org.coupon.couponservice.domain.QIssuedCoupon.issuedCoupon;

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
        return Optional.ofNullable(count).orElse(0L);
    }
}
