package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, Long>, IssuedCouponRepositoryCustom {

    /**
     * 주문 사가가 적용할 발급 건을 찾는다.
     *
     * <p>주문 요청은 내부 PK가 아니라 {@code couponId}를 실어 오므로 여기서 해석한다.
     * {@code UNIQUE(user_id, coupon_id)} 덕분에 결과는 많아야 1건이다 —
     * 그 제약이 없으면 "어느 발급 건을 쓸 것인가"가 모호해진다.
     */
    Optional<IssuedCoupon> findByUserIdAndCouponIdAndStatus(Long userId, Long couponId, IssuedCouponStatus status);

    /** 상태를 가리지 않고 찾는다. 보상 경로에서 이미 USED인 건을 되돌릴 때 쓴다. */
    Optional<IssuedCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
}
