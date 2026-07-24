package org.coupon.couponservice.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.couponservice.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 게이트웨이가 전달한 Access Token을 <b>재검증</b>해 SecurityContext를 채운다.
 *
 * <p>목적이 둘이다.
 * <ul>
 *     <li>게이트웨이를 우회한 직접 호출 차단. 재검증이 없으면 내부망에 닿는 누구나 인증 없이 발급할 수 있다.</li>
 *     <li>발급 주체 복원. 컨트롤러가 {@code @AuthenticationPrincipal}로 userId를 꺼낼 수 있게 한다.</li>
 * </ul>
 *
 * <p>실패해도 여기서 응답을 만들지 않는다. 원인만 요청 속성에 남기고 통과시키면
 * {@link JwtAuthenticationEntryPoint}가 그 값을 읽어 401 본문을 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACTUATOR_PATH_PREFIX = "/actuator/";

    static final String EXCEPTION_ATTRIBUTE = "exception";

    private final JwtTokenProvider jwtTokenProvider;

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
            // 필터는 DispatcherServlet 밖이라 @RestControllerAdvice가 잡지 못한다.
            // 원인만 남기고 통과시키면 EntryPoint가 만료/위조를 구분해 응답한다.
            log.debug("토큰 인증 실패: errorCode={}", e.getErrorCode().getCode());
            request.setAttribute(EXCEPTION_ATTRIBUTE, e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 헬스체크는 토큰 없이 호출된다. 굳이 필터를 태워 매번 헤더를 뒤질 이유가 없다.
     * coupon-api에는 로그인 경로가 없으므로(발급은 user-service) 제외 대상은 이것뿐이다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ACTUATOR_PATH_PREFIX);
    }

    /**
     * Authorization 헤더에서 "Bearer " 접두사를 제거하고 토큰 값만 추출한다.
     * 헤더가 없거나 형식이 다르면 null — 인증 없는 요청으로 취급한다.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 토큰을 검증하고 유효하면 SecurityContext에 인증 정보를 설정한다.
     * 여기에 담긴 값을 비즈니스 로직이 {@code @AuthenticationPrincipal}로 꺼내 쓴다.
     */
    private void authenticateUser(String token, HttpServletRequest request) {
        jwtTokenProvider.validateToken(token);

        // Refresh Token은 재발급 전용이다. 서명이 유효하다는 이유로 API 인증에 쓰이면
        // 수명 30분인 Access Token 대신 7일짜리 토큰으로 API를 호출할 수 있게 된다.
        if (!jwtTokenProvider.isAccessToken(token)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Access Token이 아닙니다");
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
