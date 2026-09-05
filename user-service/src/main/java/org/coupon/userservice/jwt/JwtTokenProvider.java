package org.coupon.userservice.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.security.JwtSecretProperties;
import org.coupon.common.security.JwtTokenContract;
import org.coupon.userservice.domain.UserRole;
import org.coupon.userservice.exception.ExpiredTokenException;
import org.coupon.userservice.exception.InvalidTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final long MILLIS_PER_SECOND = 1000L;

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long accessTokenValidityMillis;
    private final long refreshTokenValidityMillis;

    public JwtTokenProvider(JwtSecretProperties jwtProperties,
                            @Value("${jwt.access-token-validity}") long accessTokenValidity,
                            @Value("${jwt.refresh-token-validity}") long refreshTokenValidity) {
        this.key = jwtProperties.toSecretKey();
        this.accessTokenValiditySeconds = accessTokenValidity;
        this.accessTokenValidityMillis = accessTokenValidity * MILLIS_PER_SECOND;
        this.refreshTokenValidityMillis = refreshTokenValidity * MILLIS_PER_SECOND;
    }

    public String generateAccessToken(Long userId, String username, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(JwtTokenContract.CLAIM_USERNAME, username)
                .claim(JwtTokenContract.CLAIM_ROLE, role.name())
                .claim(JwtTokenContract.CLAIM_TYPE, JwtTokenContract.TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidityMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(JwtTokenContract.CLAIM_TYPE, JwtTokenContract.TYPE_REFRESH)
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
            log.warn("토큰의 sub가 숫자가 아닙니다");
            throw new InvalidTokenException("토큰의 사용자 식별자 형식이 올바르지 않습니다");
        }
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).get(JwtTokenContract.CLAIM_USERNAME, String.class);
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(JwtTokenContract.CLAIM_ROLE, String.class);
    }

    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    public boolean isRefreshToken(String token) {
        return JwtTokenContract.TYPE_REFRESH
                .equals(parseClaims(token).get(JwtTokenContract.CLAIM_TYPE, String.class));
    }

    public boolean isAccessToken(String token) {
        return JwtTokenContract.TYPE_ACCESS
                .equals(parseClaims(token).get(JwtTokenContract.CLAIM_TYPE, String.class));
    }

    public Date getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration();
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

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
