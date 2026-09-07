package org.coupon.apigateway.config;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 게이트웨이는 인증(401)만 담당한다. 권한(403)은 각 서비스가 판단한다.
 * 테스트 환경에는 다운스트림 서비스가 없어 "시큐리티를 통과했다"는 404로 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static final String PROTECTED_PATH = "/api/v1/coupons/1";
    private static final long HOUR_MILLIS = 3600_000L;

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String secret;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("로그인과 회원가입은 토큰 없이 통과한다")
    void publicPathsPassWithoutToken() {
        // given
        String login = "/api/v1/auth/login";
        String signup = "/api/v1/auth/signup";

        // when & then
        webTestClient.post().uri(login).exchange().expectStatus().isNotFound();
        webTestClient.post().uri(signup).exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("보호된 경로를 토큰 없이 부르면 401이다")
    void protectedPathWithoutTokenIsUnauthorized() {
        // when & then
        webTestClient.get().uri(PROTECTED_PATH)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("만료되거나 서명이 다른 토큰은 401이다")
    void expiredOrForgedTokenIsUnauthorized() {
        // given
        String expired = accessToken(-HOUR_MILLIS, secret);
        String forged = accessToken(HOUR_MILLIS, "another-secret-key-that-is-at-least-32-bytes");

        // when & then
        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Refresh Token으로는 API를 호출할 수 없다")
    void refreshTokenIsUnauthorized() {
        // given
        String refresh = token("refresh", HOUR_MILLIS, secret);

        // when & then
        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("유효한 Access Token은 시큐리티를 통과한다")
    void validAccessTokenPasses() {
        // given
        String access = accessToken(HOUR_MILLIS, secret);

        // when & then
        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .exchange()
                .expectStatus().isNotFound();
    }

    private String accessToken(long validityMillis, String signingSecret) {
        return token("access", validityMillis, signingSecret);
    }

    private String token(String type, long validityMillis, String signingSecret) {
        SecretKey key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.builder()
                .subject("1")
                .claim("username", "tester")
                .claim("role", "USER")
                .claim("type", type)
                .expiration(new Date(System.currentTimeMillis() + validityMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
