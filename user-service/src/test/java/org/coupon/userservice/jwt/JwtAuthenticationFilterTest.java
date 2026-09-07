package org.coupon.userservice.jwt;

import org.coupon.userservice.exception.InvalidTokenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String ACCESS = "access.token";

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그아웃(블랙리스트)된 Access Token으로는 인증되지 않는다")
    void blacklistedAccessTokenIsNotAuthenticated() throws Exception {
        // given
        when(jwtTokenProvider.validateToken(ACCESS)).thenReturn(true);
        when(jwtTokenProvider.isAccessToken(ACCESS)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(ACCESS)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer " + ACCESS);

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE))
                .isInstanceOf(InvalidTokenException.class);
    }
}
