package org.coupon.userservice.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    private static final String TOKEN = "header.payload.signature";
    private static final String KEY = "blacklist:" + TOKEN;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("이미 만료된 토큰은 블랙리스트에 저장하지 않는다")
    void expired_token_is_not_stored() {
        Date expired = new Date(System.currentTimeMillis() - 1_000L);

        tokenBlacklistService.addToBlacklist(TOKEN, expired);

        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("유효한 토큰은 남은 수명만큼의 TTL로 저장된다")
    void valid_token_is_stored_with_remaining_ttl() {
        long remaining = 60_000L;
        Date expiry = new Date(System.currentTimeMillis() + remaining);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        tokenBlacklistService.addToBlacklist(TOKEN, expiry);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(KEY), eq("blacklisted"), ttl.capture());
        assertThat(ttl.getValue().toMillis()).isPositive().isLessThanOrEqualTo(remaining);
    }

    @Test
    @DisplayName("키가 있으면 블랙리스트로 판정한다")
    void blacklisted_when_key_exists() {
        given(redisTemplate.hasKey(KEY)).willReturn(true);

        assertThat(tokenBlacklistService.isBlacklisted(TOKEN)).isTrue();
    }

    @Test
    @DisplayName("키가 없으면 블랙리스트가 아니다")
    void not_blacklisted_when_key_missing() {
        given(redisTemplate.hasKey(KEY)).willReturn(false);

        assertThat(tokenBlacklistService.isBlacklisted(TOKEN)).isFalse();
    }
}
