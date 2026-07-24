package org.coupon.couponservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CouponCountRedisRepository {

    private static final Duration COUNT_TTL = Duration.ofDays(1);

    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public Long increment(Long couponId) {
        return redisTemplate.execute(
                INCR_WITH_TTL,
                List.of(countKey(couponId)),
                String.valueOf(COUNT_TTL.toSeconds()));
    }

    public void decrement(Long couponId) {
        redisTemplate.opsForValue().decrement(countKey(couponId));
    }

    private String countKey(Long couponId) {
        return "coupon:" + couponId + ":count";
    }
}
