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

    /**
     * 이미 받은 쿠폰 제외는 안티 조인이다. {@code userId} 조건이 {@code on}에 있어야 한다 —
     * {@code where}로 내리면 LEFT JOIN이 INNER JOIN으로 무너져 결과가 정반대가 된다.
     *
     * <p>{@code UNIQUE(user_id, coupon_id)}가 쿠폰당 매칭 행을 최대 1건으로 강제하므로
     * {@code distinct}가 필요 없고, on 조건이 그 인덱스를 전체 키로 탐색한다.
     */
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
