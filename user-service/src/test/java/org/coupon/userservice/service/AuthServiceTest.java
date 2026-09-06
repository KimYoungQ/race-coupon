package org.coupon.userservice.service;

import org.coupon.userservice.domain.User;
import org.coupon.userservice.domain.UserRole;
import org.coupon.userservice.dto.request.TokenRefreshRequest;
import org.coupon.userservice.dto.response.TokenResponse;
import org.coupon.userservice.exception.InvalidTokenException;
import org.coupon.userservice.jwt.JwtTokenProvider;
import org.coupon.userservice.jwt.TokenBlacklistService;
import org.coupon.userservice.mapper.UserMapper;
import org.coupon.userservice.repository.RefreshTokenRedisRepository;
import org.coupon.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long USER_ID = 7L;
    private static final String ACCESS = "access.token";
    private static final String REFRESH = "refresh.token";
    private static final long REFRESH_TTL_MILLIS = 604_800_000L;

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRedisRepository refreshTokenRedisRepository;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("로그아웃은 Refresh Token을 먼저 삭제한 뒤 Access Token을 블랙리스트에 넣는다")
    void logout_deletes_refresh_token_before_blacklisting_access_token() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000L);
        given(jwtTokenProvider.getUserIdFromToken(ACCESS)).willReturn(USER_ID);
        given(jwtTokenProvider.getExpirationFromToken(ACCESS)).willReturn(expiry);

        authService.logout(ACCESS);

        InOrder inOrder = inOrder(refreshTokenRedisRepository, tokenBlacklistService);
        inOrder.verify(refreshTokenRedisRepository).deleteByUserId(USER_ID);
        inOrder.verify(tokenBlacklistService).addToBlacklist(ACCESS, expiry);
    }

    @Test
    @DisplayName("저장된 토큰과 다르거나 없어(로그아웃·이미 회전) 교체에 실패하면 재발급을 거절한다")
    void refresh_is_rejected_when_rotate_fails() {
        givenValidRefreshTokenAndNewTokens();
        given(refreshTokenRedisRepository.rotate(USER_ID, REFRESH, "new.refresh", REFRESH_TTL_MILLIS))
                .willReturn(false);

        assertThatThrownBy(() -> authService.refresh(request(REFRESH)))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRedisRepository, never()).save(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("저장된 토큰과 같을 때만 원자적으로 교체하고 새 토큰을 응답한다")
    void refresh_rotates_atomically_and_returns_new_tokens() {
        givenValidRefreshTokenAndNewTokens();
        given(refreshTokenRedisRepository.rotate(USER_ID, REFRESH, "new.refresh", REFRESH_TTL_MILLIS))
                .willReturn(true);
        given(jwtTokenProvider.getAccessTokenValiditySeconds()).willReturn(1800L);

        TokenResponse response = authService.refresh(request(REFRESH));

        assertThat(response.getAccessToken()).isEqualTo("new.access");
        assertThat(response.getRefreshToken()).isEqualTo("new.refresh");
        verify(refreshTokenRedisRepository).rotate(USER_ID, REFRESH, "new.refresh", REFRESH_TTL_MILLIS);
        verify(refreshTokenRedisRepository, never()).save(anyLong(), anyString(), anyLong());
    }

    private void givenValidRefreshTokenAndNewTokens() {
        given(jwtTokenProvider.validateToken(REFRESH)).willReturn(true);
        given(jwtTokenProvider.isRefreshToken(REFRESH)).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken(REFRESH)).willReturn(USER_ID);

        User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(user.getUsername()).willReturn("tester");
        given(user.getRole()).willReturn(UserRole.USER);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        given(jwtTokenProvider.generateAccessToken(USER_ID, "tester", UserRole.USER)).willReturn("new.access");
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn("new.refresh");
        given(jwtTokenProvider.getRefreshTokenValidityMillis()).willReturn(REFRESH_TTL_MILLIS);
    }

    private TokenRefreshRequest request(String refreshToken) {
        TokenRefreshRequest request = new TokenRefreshRequest();
        ReflectionTestUtils.setField(request, "refreshToken", refreshToken);
        return request;
    }
}
