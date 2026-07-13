package org.coupon.racecoupon.service;

import lombok.RequiredArgsConstructor;
import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.domain.IssuedCoupon;
import org.coupon.racecoupon.dto.CouponIssueResponse;
import org.coupon.racecoupon.exception.CouponNotFoundException;
import org.coupon.racecoupon.exception.CouponSoldOutException;
import org.coupon.racecoupon.repository.CouponCountRedisRepository;
import org.coupon.racecoupon.repository.CouponRepository;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RedisCouponIssueService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponCountRedisRepository couponCountRedisRepository;

    @Transactional
    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        long totalQuantity = coupon.getTotalQuantity();

        Long count = couponCountRedisRepository.increment(couponId);
        if (count == null || count > totalQuantity) {
            couponCountRedisRepository.decrement(couponId);
            throw new CouponSoldOutException();
        }

        issuedCouponRepository.save(IssuedCoupon.create(userId, couponId));

        return new CouponIssueResponse(couponId, count, totalQuantity - count);
    }
}
