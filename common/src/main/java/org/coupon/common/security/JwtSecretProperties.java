package org.coupon.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "jwt")
public record JwtSecretProperties(String secret) {

    private static final int MIN_SECRET_BYTES = 32;

    private static final String HMAC_SHA256 = "HmacSHA256";

    public JwtSecretProperties {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "jwt.secret이 설정되지 않았습니다. config/application-{profile}.yml에 지정하세요.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret이 너무 짧습니다. HS256은 최소 " + MIN_SECRET_BYTES + "바이트를 요구합니다.");
        }
    }

    public SecretKey toSecretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
    }
}
