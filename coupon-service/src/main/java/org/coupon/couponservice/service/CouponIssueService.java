package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.exception.CouponNotFoundException;
import org.coupon.couponservice.mapper.CouponMapper;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponMapper couponMapper;

    @Transactional
    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = getCoupon(couponId);

        coupon.issue();
        couponRepository.save(coupon);
        issuedCouponRepository.save(IssuedCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .build());

        return couponMapper.toIssueResponse(coupon);
    }

    @Transactional(readOnly = true)
    public CouponIssueResponse getCouponInfo(Long couponId) {
        return couponMapper.toIssueResponse(getCoupon(couponId));
    }

    private Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
    }
}
