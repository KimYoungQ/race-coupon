package org.coupon.racecoupon.service;

import lombok.RequiredArgsConstructor;
import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.domain.IssuedCoupon;
import org.coupon.racecoupon.dto.CouponIssueResponse;
import org.coupon.racecoupon.exception.CouponNotFoundException;
import org.coupon.racecoupon.exception.CouponSoldOutException;
import org.coupon.racecoupon.repository.CouponRepository;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RedisCouponIssueService {

    private static final String COUNT_KEY_PREFIX = "coupon:count:";

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        long totalQuantity = coupon.getTotalQuantity();

        String countKey = COUNT_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count == null || count > totalQuantity) {
            redisTemplate.opsForValue().decrement(countKey);
            throw new CouponSoldOutException();
        }

        issuedCouponRepository.save(IssuedCoupon.create(userId, couponId));

        return new CouponIssueResponse(couponId, count, totalQuantity - count);
    }
}
