package org.coupon.couponservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CouponIssueRedisRepository {

    private static final RedisScript<Long> ISSUE = RedisScript.of(
            new ClassPathResource("redis/issue_coupon.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * @return 양수면 확정된 누적 발급 수, 0이면 이미 발급받은 사용자, -1이면 소진, -2면 TTL 무효
     */
    public long issue(Long couponId, Long userId, long limit, long ttlSeconds) {
        Long result = redisTemplate.execute(
                ISSUE,
                List.of(seqKey(couponId), issuedKey(couponId)),
                String.valueOf(userId), String.valueOf(limit), String.valueOf(ttlSeconds));

        if (result == null) {
            throw new IllegalStateException("발급 스크립트가 값을 반환하지 않았다: couponId=" + couponId);
        }
        return result;
    }

    private String seqKey(Long couponId) {
        return "coupon:" + couponId + ":seq";
    }

    private String issuedKey(Long couponId) {
        return "coupon:" + couponId + ":issued";
    }

}
