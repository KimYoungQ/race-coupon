package org.coupon.couponservice.controller;

import io.jsonwebtoken.Jwts;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.jwt.TokenBlacklistService;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.support.MySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
class CouponSecurityTest {

    private static final long HOUR_MILLIS = 3600_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CouponRepository couponRepository;

    @Value("${jwt.secret}")
    private String secret;

    // 블랙리스트 조회는 Redis 를 쓰므로 Mock 으로 대체한다 (기본값 false = 블랙리스트 아님)
    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @AfterEach
    void tearDown() {
        couponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 이다")
    void requestWithoutTokenIsUnauthorized() throws Exception {
        // given
        Long couponId = saveCoupon();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("USER 권한으로 관리자 API 를 호출하면 403 이다")
    void userRoleIsForbiddenOnAdminApi() throws Exception {
        // given
        String userToken = accessToken(1L, "USER");

        // when & then
        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 권한으로 관리자 API 를 호출하면 성공한다")
    void adminRoleCanCallAdminApi() throws Exception {
        // given
        String adminToken = accessToken(1L, "ADMIN");

        // when & then
        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCouponBody()))
                .andExpect(status().isCreated());
    }

    private Long saveCoupon() {
        return couponRepository.save(Coupon.builder()
                .title("선착순 쿠폰")
                .totalQuantity(100L)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .eventEndAt(LocalDateTime.now().plusDays(1))
                .build()).getId();
    }

    private String createCouponBody() {
        return """
                {
                  "title": "관리자 등록 쿠폰",
                  "totalQuantity": 50,
                  "discountType": "PERCENT",
                  "discountValue": 20,
                  "eventEndAt": "%s"
                }
                """.formatted(LocalDateTime.now().plusDays(1));
    }

    private String accessToken(Long userId, String role) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", "user" + userId)
                .claim("role", role)
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + HOUR_MILLIS))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
