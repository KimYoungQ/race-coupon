package org.coupon.racecoupon.controller;

import io.jsonwebtoken.Jwts;
import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.domain.DiscountType;
import org.coupon.racecoupon.repository.CouponRepository;
import org.coupon.racecoupon.repository.IssuedCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CouponSecurityTest {

    private static final long HOUR_MILLIS = 3600_000L;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Value("${jwt.secret}")
    private String secret;

    private MockMvc mockMvc;
    private Long couponId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        couponId = couponRepository.save(
                Coupon.create("선착순 쿠폰", 100L, DiscountType.PERCENT, 10L)).getId();
    }

    @Test
    @DisplayName("토큰 없이 발급하면 401이다")
    void issue_without_token_is_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("만료된 토큰으로 발급하면 401이다")
    void issue_with_expired_token_is_unauthorized() throws Exception {
        String expired = token(1L, "USER", "access", -HOUR_MILLIS);

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("서명이 다른 토큰으로 발급하면 401이다")
    void issue_with_forged_token_is_unauthorized() throws Exception {
        SecretKey otherKey = new SecretKeySpec(
                "another-secret-key-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        String forged = Jwts.builder()
                .subject("1").claim("role", "USER").claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + HOUR_MILLIS))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Refresh Token으로는 API를 호출할 수 없다")
    void issue_with_refresh_token_is_unauthorized() throws Exception {
        String refresh = token(1L, null, "refresh", HOUR_MILLIS);

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("USER 토큰으로 발급하면 201이고 발급 주체는 토큰의 sub다")
    void issue_with_user_token_records_subject_as_owner() throws Exception {
        long userId = 42L;

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId, "USER", "access", HOUR_MILLIS)))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(issuedCouponRepository.findAll())
                .singleElement()
                .extracting("userId")
                .isEqualTo(userId);
    }

    @Test
    @DisplayName("USER 토큰으로 관리자 쿠폰 등록을 시도하면 403이다")
    void create_coupon_with_user_token_is_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "USER", "access", HOUR_MILLIS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 토큰으로 관리자 쿠폰 등록을 하면 201이다")
    void create_coupon_with_admin_token_is_created() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "ADMIN", "access", HOUR_MILLIS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("관리자 등록 쿠폰"));
    }

    @Test
    @DisplayName("조회도 인증이 필요하다")
    void get_coupon_without_token_is_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/coupons/{couponId}", couponId))
                .andExpect(status().isUnauthorized());
    }

    private String createRequestBody() {
        return """
                {
                  "title": "관리자 등록 쿠폰",
                  "totalQuantity": 50,
                  "discountType": "PERCENT",
                  "discountValue": 20
                }
                """;
    }

    private String token(Long userId, String role, String type, long validityMillis) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", "user" + userId)
                .claim("type", type)
                .expiration(new Date(System.currentTimeMillis() + validityMillis))
                .signWith(key, Jwts.SIG.HS256);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }
}
