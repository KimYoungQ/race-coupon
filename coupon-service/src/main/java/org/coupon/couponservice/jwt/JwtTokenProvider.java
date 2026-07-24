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

/**
 * JWT <b>검증 전용</b>. 발급은 user-service의 동명 클래스가 담당하고 여기서는 하지 않는다.
 *
 * <p>게이트웨이가 이미 검증한 토큰을 한 번 더 본다. 중복처럼 보이지만, 이 검증이 없으면
 * 게이트웨이를 건너뛰고 8081로 직접 호출하는 경로가 무방비로 열린다.
 *
 * <p>읽는 클레임은 user-service가 발급하는 것과 정확히 같아야 한다({@link JwtTokenContract}):
 * {@code sub}=userId, {@code username}, {@code role}(접두사 없음), {@code type}.
 */
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

    /**
     * 서명과 만료를 검증한다.
     *
     * <p>실패를 false로 뭉뚱그리지 않고 예외로 구분해 던진다. 호출부가 만료(재발급 유도)와
     * 위조(거부)를 다르게 처리해야 하기 때문이다.
     *
     * @throws BusinessException 만료(TOKEN_EXPIRED) 또는 위조·형식 오류(INVALID_TOKEN)
     */
    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    /**
     * API 인증에 쓸 수 있는 토큰인지 확인한다.
     * Refresh Token도 같은 키로 서명되므로 이 검사가 없으면 서명·만료 검증만으로는 걸러지지 않는다.
     */
    public boolean isAccessToken(String token) {
        return JwtTokenContract.TYPE_ACCESS
                .equals(parseClaims(token).get(JwtTokenContract.CLAIM_TYPE, String.class));
    }

    /**
     * 발급 주체. 토큰의 sub는 문자열이지만 이 시스템에서는 항상 userId다.
     * 숫자가 아니라면 서명이 유효해도 우리가 발급한 토큰이 아니다.
     */
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

    /** 접두사 없는 역할명("ADMIN"). ROLE_ 부착은 AuthenticatedUser.from(...)이 담당한다. */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get(JwtTokenContract.CLAIM_ROLE, String.class);
    }

    /**
     * 파싱·서명·만료 검증을 한곳에 모아 jjwt 예외를 도메인 예외로 번역한다.
     * 토큰 값 자체는 탈취 위험이 있어 절대 로그에 남기지 않는다.
     */
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
