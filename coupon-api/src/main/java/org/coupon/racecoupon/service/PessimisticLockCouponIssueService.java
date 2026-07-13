package org.coupon.racecoupon.service;

import lombok.RequiredArgsConstructor;
import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.domain.IssuedCoupon;
import org.coupon.racecoupon.dto.CouponIssueResponse;
import org.coupon.racecoupon.exception.CouponNotFoundException;
import org.coupon.racecoupon.repository.CouponRepository;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticLockCouponIssueService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findWithPessimisticLockById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        coupon.issue();
        couponRepository.save(coupon);
        issuedCouponRepository.save(IssuedCoupon.create(userId, couponId));

        return CouponIssueResponse.from(coupon);
    }
}
