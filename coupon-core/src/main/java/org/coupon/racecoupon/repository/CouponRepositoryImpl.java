package org.coupon.racecoupon.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.coupon.racecoupon.domain.Coupon;

import java.util.Optional;

import static org.coupon.racecoupon.domain.QCoupon.coupon;

@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Coupon> findWithPessimisticLockById(Long id) {
        Coupon result = queryFactory
                .selectFrom(coupon)
                .where(coupon.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }
}
