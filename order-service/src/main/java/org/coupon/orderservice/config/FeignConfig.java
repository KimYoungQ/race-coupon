package org.coupon.orderservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.coupon.orderservice.client.ApiResponseErrorDecoder;
import org.coupon.orderservice.client.AuthorizationRelayInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 여기 빈들은 모든 Feign 클라이언트에 공통 적용된다. 타임아웃·로깅 레벨은 config 서버 yml 에서 관리
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor authorizationRelayInterceptor() {
        return new AuthorizationRelayInterceptor();
    }

    @Bean
    public ErrorDecoder apiResponseErrorDecoder(ObjectMapper objectMapper) {
        return new ApiResponseErrorDecoder(objectMapper);
    }
}
