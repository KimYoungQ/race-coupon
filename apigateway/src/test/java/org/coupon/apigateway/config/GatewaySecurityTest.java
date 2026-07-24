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

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("토큰 없이 보호된 경로를 부르면 401이다")
    void protected_path_without_token_is_unauthorized() {
        webTestClient.get().uri(PROTECTED_PATH)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("만료된 토큰은 401이다")
    void expired_token_is_unauthorized() {
        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token("USER", "access", -HOUR_MILLIS, secret)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("서명이 다른 토큰은 401이다")
    void forged_token_is_unauthorized() {
        String otherSecret = "another-secret-key-that-is-at-least-32-bytes";

        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token("USER", "access", HOUR_MILLIS, otherSecret)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Refresh Token으로는 API를 호출할 수 없다")
    void refresh_token_is_unauthorized() {
        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token(null, "refresh", HOUR_MILLIS, secret)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("유효한 Access Token은 시큐리티를 통과한다")
    void valid_access_token_passes_security() {
        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token("USER", "access", HOUR_MILLIS, secret)))
                .exchange()
                // 테스트에는 라우트가 없어 404가 난다. 401이 아니라는 것이 통과의 증거다.
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("로그인은 토큰 없이 통과한다")
    void login_is_permitted_without_token() {
        webTestClient.post().uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("회원가입은 POST만 열려 있다")
    void signup_is_permitted_only_for_post() {
        webTestClient.post().uri("/api/v1/auth/signup")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get().uri("/api/v1/auth/signup")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("모든 응답에 보안 헤더 4종이 붙는다")
    void security_headers_are_present() {
        HttpHeaders headers = webTestClient.get().uri(PROTECTED_PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .returnResult(Void.class)
                .getResponseHeaders();

        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("same-origin");
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'self'; frame-ancestors 'none'; base-uri 'self'");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String token(String role, String type, long validityMillis, String signingSecret) {
        SecretKey key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var builder = Jwts.builder()
                .subject("1")
                .claim("username", "tester")
                .claim("type", type)
                .expiration(new Date(System.currentTimeMillis() + validityMillis))
                .signWith(key, Jwts.SIG.HS256);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }
}
