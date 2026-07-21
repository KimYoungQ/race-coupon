package org.coupon.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 검증 설정. secret 은 HS256 서명 검증용 비밀키(최소 32바이트).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {
}
