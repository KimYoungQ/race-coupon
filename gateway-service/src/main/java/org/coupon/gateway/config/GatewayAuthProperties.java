package org.coupon.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 게이트웨이 인증 정책. publicPaths 는 JWT 검증 없이 통과시킬 경로(AntPath 패턴).
 */
@ConfigurationProperties(prefix = "gateway.auth")
public record GatewayAuthProperties(List<String> publicPaths) {

    public GatewayAuthProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
