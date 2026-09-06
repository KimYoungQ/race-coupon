package org.coupon.orderservice.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.orderservice.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACTUATOR_PATH_PREFIX = "/actuator/";

    static final String EXCEPTION_ATTRIBUTE = "exception";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractTokenFromRequest(request);
            if (token != null) {
                authenticateUser(token, request);
            }
        } catch (BusinessException e) {
            log.debug("토큰 인증 실패: errorCode={}", e.getErrorCode().getCode());
            request.setAttribute(EXCEPTION_ATTRIBUTE, e);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ACTUATOR_PATH_PREFIX);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void authenticateUser(String token, HttpServletRequest request) {
        jwtTokenProvider.validateToken(token);

        if (!jwtTokenProvider.isAccessToken(token)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Access Token이 아닙니다");
        }

        if (tokenBlacklistService.isBlacklisted(token)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "로그아웃된 토큰입니다");
        }

        AuthenticatedUser principal = AuthenticatedUser.from(
                jwtTokenProvider.getUserIdFromToken(token),
                jwtTokenProvider.getUsernameFromToken(token),
                jwtTokenProvider.getRoleFromToken(token));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("인증 성공: userId={}, username={}", principal.getUserId(), principal.getUsername());
    }
}
