package org.coupon.couponservice.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.dto.MyCouponResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.coupon.couponservice.domain.QCoupon.coupon;
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

    public List<MyCouponResponse> findMyCoupons(Long userId) {
        return queryFactory
                .select(Projections.constructor(MyCouponResponse.class,
                        coupon.id,
                        coupon.title,
                        coupon.discountType,
                        coupon.discountValue,
                        coupon.maxDiscountAmount,
                        coupon.minOrderAmount,
                        coupon.eventEndAt,
                        issuedCoupon.status,
                        issuedCoupon.issuedAt,
                        issuedCoupon.usedAt,
                        issuedCoupon.orderId))
                .from(issuedCoupon)
                .join(coupon).on(coupon.id.eq(issuedCoupon.couponId))
                .where(issuedCoupon.userId.eq(userId))
                .orderBy(
                        new CaseBuilder()
                                .when(issuedCoupon.status.eq(IssuedCouponStatus.ISSUED)).then(0)
                                .otherwise(1).asc(),
                        issuedCoupon.issuedAt.desc(),
                        issuedCoupon.id.desc())
                .fetch();
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
