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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisCouponIssueService {

    private static final Duration COUNT_TTL = Duration.ofDays(1);

    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        long totalQuantity = coupon.getTotalQuantity();

        String countKey = countKey(couponId);
        Long count = redisTemplate.execute(
                INCR_WITH_TTL,
                List.of(countKey),
                String.valueOf(COUNT_TTL.toSeconds()));
        if (count == null || count > totalQuantity) {
            redisTemplate.opsForValue().decrement(countKey);
            throw new CouponSoldOutException();
        }

        issuedCouponRepository.save(IssuedCoupon.create(userId, couponId));

        return new CouponIssueResponse(couponId, count, totalQuantity - count);
    }

    private String countKey(Long couponId) {
        return "coupon:" + couponId + ":count";
    }
}
