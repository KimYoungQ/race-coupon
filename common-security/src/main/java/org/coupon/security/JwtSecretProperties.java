package org.coupon.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HS256 서명 검증용 비밀키. user-service가 토큰 서명에 쓰는 값과 반드시 같아야 한다.
 *
 * <p>이 클래스는 컴포넌트 스캔 범위 밖({@code org.coupon.security})에 있으므로 각 서비스가
 * {@code @EnableConfigurationProperties(JwtSecretProperties.class)}로 명시적으로 등록한다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtSecretProperties(String secret) {

    /** HS256(HMAC-SHA256)이 요구하는 최소 키 길이. */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String HMAC_SHA256 = "HmacSHA256";

    public JwtSecretProperties {
        // @ConfigurationProperties 바인딩은 @Value와 달리 해결하지 못한 플레이스홀더를
        // 예외 없이 "${JWT_SECRET}" 리터럴로 남긴다. 그대로 두면 환경변수를 빼먹어도
        // 조용히 기동해서 예측 가능한 키로 서명을 검증하게 되므로 여기서 직접 막는다.
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "JWT_SECRET이 설정되지 않았습니다. 저장소 루트의 .env에 JWT_SECRET을 지정하세요.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET이 너무 짧습니다. HS256은 최소 " + MIN_SECRET_BYTES + "바이트를 요구합니다.");
        }
    }

    /**
     * 서명 검증용 대칭키. 게이트웨이(리액티브)와 coupon-api(서블릿)가 같은 방식으로 키를 만들어야
     * 한쪽만 통과하는 401이 생기지 않는다.
     */
    public SecretKey toSecretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
    }
}
