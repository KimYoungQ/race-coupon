package org.coupon.userservice.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.coupon.userservice.domain.UserRole;
import org.coupon.userservice.exception.ExpiredTokenException;
import org.coupon.userservice.exception.InvalidTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급·검증 담당. 이 시스템에서 토큰을 만드는 유일한 지점이다.
 *
 * <p>토큰 클레임 계약(게이트웨이 SecurityConfig와 반드시 일치해야 한다):
 * <ul>
 *     <li>Access : sub=userId, username, role(ROLE_ 접두사 없음), type="access", iat, exp</li>
 *     <li>Refresh: sub=userId, type="refresh", iat, exp</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final long MILLIS_PER_SECOND = 1000L;

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long accessTokenValidityMillis;
    private final long refreshTokenValidityMillis;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-token-validity}") long accessTokenValidity,
                            @Value("${jwt.refresh-token-validity}") long refreshTokenValidity) {
        // 환경변수를 빼먹은 채 조용히 기동해 예측 가능한 키로 토큰을 발급하는 사고를 막는다.
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "JWT_SECRET이 설정되지 않았습니다. 저장소 루트의 .env에 JWT_SECRET을 지정하세요.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValidity;
        this.accessTokenValidityMillis = accessTokenValidity * MILLIS_PER_SECOND;
        this.refreshTokenValidityMillis = refreshTokenValidity * MILLIS_PER_SECOND;
    }

    public String generateAccessToken(Long userId, String username, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidityMillis))
                // 알고리즘을 명시하지 않으면 jjwt가 비밀키 길이로 HS384/HS512를 골라버린다.
                // 게이트웨이 디코더는 HS256으로 고정돼 있어 그 경우 토큰이 거부된다.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh Token에는 재발급에 필요한 최소 정보(sub)만 담는다.
     * 수명이 길어 탈취 시 노출 범위가 크므로 username·role은 싣지 않는다.
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenValidityMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        String subject = parseClaims(token).getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            // 서명은 유효하지만 sub가 userId 형식이 아니다 = 우리가 발급한 토큰이 아니다.
            log.warn("토큰의 sub가 숫자가 아닙니다");
            throw new InvalidTokenException("토큰의 사용자 식별자 형식이 올바르지 않습니다");
        }
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).get(CLAIM_USERNAME, String.class);
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    /**
     * 검증에 성공하면 true, 실패하면 원인을 구분할 수 있도록 예외를 던진다.
     * 호출부가 만료(재발급 유도)와 위조(거부)를 다르게 처리해야 하므로 false로 뭉뚱그리지 않는다.
     */
    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    /**
     * API 인증에 쓸 수 있는 토큰인지 확인한다.
     * Refresh Token도 같은 키로 서명되므로 이 검사가 없으면 서명·만료 검증만으로는 걸러지지 않는다.
     */
    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    public Date getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * LoginResponse.expiresIn 응답용. 밀리초가 아닌 설정 원본(초) 그대로 반환한다.
     */
    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    /**
     * 파싱·서명·만료 검증을 한곳에 모아 jjwt 예외를 도메인 예외로 번역한다.
     * 토큰 값 자체는 탈취 위험이 있어 절대 로그에 남기지 않는다.
     */
    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰 접근");
            throw new ExpiredTokenException();
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException
                 | IllegalArgumentException e) {
            log.warn("유효하지 않은 토큰: reason={}", e.getClass().getSimpleName());
            throw new InvalidTokenException("유효하지 않은 토큰입니다");
        }
    }
}
