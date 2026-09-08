package org.coupon.couponservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.IssuedCouponStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.coupon.couponservice.domain.QIssuedCoupon.issuedCoupon;

@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public long countByCouponId(Long couponId) {
        Long count = queryFactory
                .select(issuedCoupon.count())
                .from(issuedCoupon)
                .where(issuedCoupon.couponId.eq(couponId))
                .fetchOne();
        return Optional.ofNullable(count).orElse(0L);
    }

    public IssuedCouponStatus findStatusBy(Long userId, Long couponId) {
        return queryFactory
                .select(issuedCoupon.status)
                .from(issuedCoupon)
                .where(
                        issuedCoupon.userId.eq(userId),
                        issuedCoupon.couponId.eq(couponId))
                .fetchOne();
    }

    public long markUsed(Long userId, Long couponId, Long orderId, LocalDateTime usedAt) {
        return queryFactory
                .update(issuedCoupon)
                .set(issuedCoupon.status, IssuedCouponStatus.USED)
                .set(issuedCoupon.orderId, orderId)
                .set(issuedCoupon.usedAt, usedAt)
                .where(
                        issuedCoupon.userId.eq(userId),
                        issuedCoupon.couponId.eq(couponId),
                        issuedCoupon.status.eq(IssuedCouponStatus.ISSUED))
                .execute();
    }

    public long restore(Long userId, Long couponId, Long orderId) {
        return queryFactory
                .update(issuedCoupon)
                .set(issuedCoupon.status, IssuedCouponStatus.ISSUED)
                .setNull(issuedCoupon.orderId)
                .setNull(issuedCoupon.usedAt)
                .where(
                        issuedCoupon.userId.eq(userId),
                        issuedCoupon.couponId.eq(couponId),
                        issuedCoupon.status.eq(IssuedCouponStatus.USED),
                        issuedCoupon.orderId.eq(orderId))
                .execute();
    }
}
