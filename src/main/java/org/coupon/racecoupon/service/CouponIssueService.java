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
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = getCoupon(couponId);

        coupon.issue();
        couponRepository.save(coupon);
        issuedCouponRepository.save(IssuedCoupon.create(userId, couponId));

        return CouponIssueResponse.from(coupon);
    }

    @Transactional(readOnly = true)
    public CouponIssueResponse getCouponInfo(Long couponId) {
        return CouponIssueResponse.from(getCoupon(couponId));
    }

    private Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
    }
}
