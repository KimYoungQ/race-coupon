package org.coupon.userservice.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.userservice.exception.ExpiredTokenException;
import org.coupon.userservice.exception.InvalidTokenException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization 헤더의 Access Token을 검증해 SecurityContext에 인증을 채운다.
 * user-service 자체 보호용이며, 게이트웨이를 거치지 않는 직접 호출에도 동일하게 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String USER_ID_ATTRIBUTE = "userId";
    private static final String EXCEPTION_ATTRIBUTE = "exception";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 로그인·회원가입·토큰 재발급은 아직 토큰이 없거나 만료된 토큰을 들고 오는 경로다.
     * 여기서 검증을 돌리면 정상 요청이 401로 막힌다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                authenticate(request, token);
            } catch (ExpiredTokenException | InvalidTokenException e) {
                // 필터는 DispatcherServlet 밖이라 @ExceptionHandler가 잡지 못한다.
                // 원인만 남기고 통과시키면 EntryPoint가 이 값을 읽어 401 본문을 만든다.
                request.setAttribute(EXCEPTION_ATTRIBUTE, e);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        jwtTokenProvider.validateToken(token);

        // Refresh Token은 재발급 전용이다. 서명이 유효하다는 이유로 API 인증에 쓰이면
        // 수명이 30분인 Access Token 대신 7일짜리 토큰으로 API를 호출할 수 있게 된다.
        if (!jwtTokenProvider.isAccessToken(token)) {
            throw new InvalidTokenException("Access Token이 아닙니다");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);

        UserDetails userDetails = User.builder()
                .username(username)
                // 토큰 인증이라 비밀번호를 대조할 일이 없다. UserDetails 계약을 채우기 위한 빈 값.
                .password("")
                // role 클레임에는 ROLE_이 없으므로 여기서 붙여야 hasRole(...)이 동작한다.
                .authorities(List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role)))
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // principal에는 username만 남아 userId가 유실된다. 컨트롤러가 꺼낼 수 있게 요청 속성으로 넘긴다.
        request.setAttribute(USER_ID_ATTRIBUTE, userId);

        log.debug("인증 성공: userId={}, role={}", userId, role);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
