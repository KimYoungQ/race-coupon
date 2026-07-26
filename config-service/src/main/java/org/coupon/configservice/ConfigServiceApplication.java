package org.coupon.configservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * 설정 배포 서버. 루트 config/ 폴더의 yml을 각 서비스에 내려준다.
 *
 * <p>플레이스홀더({@code ${JWT_SECRET}} 등)는 여기서 치환하지 않고 리터럴 그대로 전달되며,
 * 각 서비스가 자신의 .env로 해석한다. 그래서 비밀값은 config/에 두지 않는다.
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
