package org.coupon.userservice.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:";
    private static final String BLACKLISTED = "blacklisted";

    private final StringRedisTemplate redisTemplate;

    public void addToBlacklist(String token, Date expiry) {
        long remaining = expiry.getTime() - System.currentTimeMillis();
        if (remaining <= 0) {
            log.debug("이미 만료된 토큰이라 블랙리스트에 넣지 않습니다");
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, BLACKLISTED, Duration.ofMillis(remaining));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
