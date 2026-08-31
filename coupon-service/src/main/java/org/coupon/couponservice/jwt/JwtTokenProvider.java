package org.coupon.couponservice.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.common.security.JwtSecretProperties;
import org.coupon.common.security.JwtTokenContract;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtSecretProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        this.secretKey = jwtProperties.toSecretKey();
    }

    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    public boolean isAccessToken(String token) {
        return JwtTokenContract.TYPE_ACCESS
                .equals(parseClaims(token).get(JwtTokenContract.CLAIM_TYPE, String.class));
    }

    public Long getUserIdFromToken(String token) {
        String subject = parseClaims(token).getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            log.warn("토큰의 sub가 숫자가 아닙니다");
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "토큰의 사용자 식별자 형식이 올바르지 않습니다");
        }
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).get(JwtTokenContract.CLAIM_USERNAME, String.class);
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(JwtTokenContract.CLAIM_ROLE, String.class);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰 접근");
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException
                 | IllegalArgumentException e) {
            log.warn("유효하지 않은 토큰: reason={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
