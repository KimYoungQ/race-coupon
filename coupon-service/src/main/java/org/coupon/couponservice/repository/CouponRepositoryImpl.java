package org.coupon.couponservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import static org.coupon.couponservice.domain.QCoupon.coupon;

@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public long increaseIssuedQuantity(Long couponId) {
        return queryFactory
                .update(coupon)
                .set(coupon.issuedQuantity, coupon.issuedQuantity.add(1))
                .where(coupon.id.eq(couponId))
                .execute();
    }

    /*
    public Optional<Coupon> findWithPessimisticLockById(Long id) {
        Coupon result = queryFactory
                .selectFrom(coupon)
                .where(coupon.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }
    */
}
