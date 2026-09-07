package org.coupon.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long USER_ID = 7L;
    private static final String ACCESS = "access.token";
    private static final String REFRESH = "refresh.token";
    private static final String NEW_ACCESS = "new.access";
    private static final String NEW_REFRESH = "new.refresh";
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
    @DisplayName("로그아웃하면 사용자의 Refresh Token이 삭제된다")
    void logoutDeletesRefreshToken() {
        // given
        Date expiry = new Date(System.currentTimeMillis() + 60_000L);
        when(jwtTokenProvider.getUserIdFromToken(ACCESS)).thenReturn(USER_ID);
        when(jwtTokenProvider.getExpirationFromToken(ACCESS)).thenReturn(expiry);

        // when
        authService.logout(ACCESS);

        // then
        verify(refreshTokenRedisRepository).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("로그아웃하면 Access Token이 블랙리스트에 등록된다")
    void logoutBlacklistsAccessToken() {
        // given
        Date expiry = new Date(System.currentTimeMillis() + 60_000L);
        when(jwtTokenProvider.getUserIdFromToken(ACCESS)).thenReturn(USER_ID);
        when(jwtTokenProvider.getExpirationFromToken(ACCESS)).thenReturn(expiry);

        // when
        authService.logout(ACCESS);

        // then
        verify(tokenBlacklistService).addToBlacklist(ACCESS, expiry);
    }

    @Test
    @DisplayName("유효한 Refresh Token이면 새 Access/Refresh Token을 재발급한다")
    void refreshReturnsNewTokens() {
        // given
        stubValidRefreshToken();
        when(refreshTokenRedisRepository.rotate(USER_ID, REFRESH, NEW_REFRESH, REFRESH_TTL_MILLIS))
                .thenReturn(true);
        when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(1800L);

        // when
        TokenResponse response = authService.refresh(request(REFRESH));

        // then
        assertThat(response.getAccessToken()).isEqualTo(NEW_ACCESS);
        assertThat(response.getRefreshToken()).isEqualTo(NEW_REFRESH);
    }

    @Test
    @DisplayName("저장된 토큰과 다른 Refresh Token(이미 회전된 이전 토큰 포함)은 재발급을 거절한다")
    void refreshIsRejectedWhenTokenDoesNotMatchStored() {
        // given
        stubValidRefreshToken();
        when(refreshTokenRedisRepository.rotate(USER_ID, REFRESH, NEW_REFRESH, REFRESH_TTL_MILLIS))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(request(REFRESH)))
                .isInstanceOf(InvalidTokenException.class);
    }

    private void stubValidRefreshToken() {
        when(jwtTokenProvider.validateToken(REFRESH)).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken(REFRESH)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(REFRESH)).thenReturn(USER_ID);

        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getUsername()).thenReturn("tester");
        when(user.getRole()).thenReturn(UserRole.USER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        when(jwtTokenProvider.generateAccessToken(USER_ID, "tester", UserRole.USER)).thenReturn(NEW_ACCESS);
        when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn(NEW_REFRESH);
        when(jwtTokenProvider.getRefreshTokenValidityMillis()).thenReturn(REFRESH_TTL_MILLIS);
    }

    // 요청 DTO는 setter가 없어 컨트롤러가 받는 것과 같은 방식(JSON 역직렬화)으로 만든다
    private TokenRefreshRequest request(String refreshToken) {
        try {
            return new ObjectMapper().readValue(
                    "{\"refreshToken\":\"" + refreshToken + "\"}", TokenRefreshRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
