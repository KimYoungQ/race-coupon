package org.coupon.couponservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.Coupon;

import java.time.LocalDateTime;
import java.util.List;

import static org.coupon.couponservice.domain.QCoupon.coupon;
import static org.coupon.couponservice.domain.QIssuedCoupon.issuedCoupon;

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

    public List<Coupon> findIssuableBy(Long userId, LocalDateTime now) {
        return queryFactory
                .selectFrom(coupon)
                .leftJoin(issuedCoupon).on(
                        issuedCoupon.couponId.eq(coupon.id),
                        issuedCoupon.userId.eq(userId))
                .where(
                        coupon.eventEndAt.gt(now),
                        coupon.issuedQuantity.lt(coupon.totalQuantity),
                        issuedCoupon.id.isNull())
                .fetch();
    }

}
