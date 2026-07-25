package org.coupon.couponservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI couponServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Coupon Service API")
                        .version("v1")
                        .description("선착순 쿠폰 발급·조회 API. 발급 주체는 검증된 토큰의 sub에서만 얻는다."))
                // "/"는 UI를 서빙한 오리진(게이트웨이 또는 서비스 포트)을 기준으로 Try it out을 보낸다.
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
