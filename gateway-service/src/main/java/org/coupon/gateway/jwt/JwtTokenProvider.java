package org.coupon.gateway.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.coupon.gateway.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT '검증' 전용. user-service가 발급한 토큰을 게이트웨이가 서명·만료 검증하고 클레임을 파싱한다.
 * 발급 로직은 게이트웨이에 없다(user-service 책임). user-service와 동일한 비밀키를 공유한다.
 * jjwt는 서블릿/리액티브와 무관한 순수 라이브러리라 WebFlux 게이트웨이에서 그대로 쓴다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 서명·만료를 검증하고 클레임을 반환한다.
     *
     * @throws io.jsonwebtoken.JwtException 서명 불일치·만료·형식 오류 등 검증 실패 시
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
