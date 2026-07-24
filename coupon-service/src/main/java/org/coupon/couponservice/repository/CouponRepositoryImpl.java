package org.coupon.couponservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.Coupon;

import java.util.Optional;

import static org.coupon.couponservice.domain.QCoupon.coupon;

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
