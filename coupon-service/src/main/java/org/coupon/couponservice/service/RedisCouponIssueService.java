package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.exception.CouponNotFoundException;
import org.coupon.couponservice.exception.CouponSoldOutException;
import org.coupon.couponservice.repository.CouponCountRedisRepository;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
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
