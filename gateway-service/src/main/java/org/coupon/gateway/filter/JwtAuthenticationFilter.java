package org.coupon.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.coupon.gateway.config.GatewayAuthProperties;
import org.coupon.gateway.jwt.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 게이트웨이에서 JWT를 1회 검증한다(발급은 user-service). 검증에 성공하면 신원(id/username/role)을
 * 다운스트림 헤더 X-User-Id / X-User-Name / X-User-Role 로 주입한다. 클라이언트가 위조로 보낸
 * 동명 헤더는 게이트웨이 검증값으로 덮어쓴다(스푸핑 방지). 공개 경로는 검증 없이 통과, 실패/누락은 401.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final GatewayAuthProperties authProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, GatewayAuthProperties authProperties) {
        this.tokenProvider = tokenProvider;
        this.authProperties = authProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 공개 경로(로그인/회원가입/문서)는 검증 없이 통과
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange);
        if (token == null) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = tokenProvider.parse(token);
            String id = String.valueOf(claims.get("id"));
            String username = String.valueOf(claims.get("username"));
            String role = String.valueOf(claims.get("role"));

            // 검증된 신원을 다운스트림 헤더로 주입. set으로 클라이언트 위조 헤더를 덮어쓴다.
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.set("X-User-Id", id);
                        headers.set("X-User-Name", username);
                        headers.set("X-User-Role", role);
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 검증 실패: {}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    private boolean isPublic(String path) {
        return authProperties.publicPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String extractToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // 라우팅 전에 인증을 강제하도록 먼저 실행
        return -1;
    }
}
