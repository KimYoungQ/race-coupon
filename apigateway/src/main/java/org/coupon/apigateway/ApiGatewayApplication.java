package org.coupon.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 단일 진입점(API Gateway). 라우팅 + JWT 인증/인가(횡단 관심사)만 담당하고 비즈니스 로직은 두지 않는다.
 * Eureka에서 lb:// 로 서비스를 발견해 라우팅한다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
