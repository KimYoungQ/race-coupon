package org.coupon.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * 서비스 레지스트리(Eureka Server). 각 서비스가 자신을 등록하고, 이름으로 서로를 발견한다.
 * 자기 자신은 클라이언트가 아니므로 register-with-eureka / fetch-registry 를 끈다(application.yml).
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
