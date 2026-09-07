package org.coupon.couponservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

@TestConfiguration(proxyBeanMethods = false)
public class RedisTestContainer {

    // GenericContainer 는 이미지 이름을 스스로 알려주지 못하므로 name 으로 Redis 연결임을 명시한다
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withReuse(true);
    }
}
