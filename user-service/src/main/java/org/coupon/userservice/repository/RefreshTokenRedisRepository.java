package org.coupon.userservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

    private static final String KEY_PREFIX = "refresh:";

    private static final RedisScript<Long> ROTATE = RedisScript.of(
            new ClassPathResource("redis/rotate_refresh_token.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String token, long ttlMillis) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, token, Duration.ofMillis(ttlMillis));
    }

    public boolean rotate(Long userId, String oldToken, String newToken, long ttlMillis) {
        Long result = redisTemplate.execute(
                ROTATE,
                List.of(KEY_PREFIX + userId),
                oldToken, newToken, String.valueOf(ttlMillis));

        if (result == null) {
            throw new IllegalStateException("회전 스크립트가 값을 반환하지 않았다: userId=" + userId);
        }
        return result == 1L;
    }

    public void deleteByUserId(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
