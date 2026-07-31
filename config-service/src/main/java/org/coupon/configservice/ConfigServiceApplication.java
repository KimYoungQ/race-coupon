package org.coupon.configservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * 설정 배포 서버. 루트 config/ 폴더의 yml을 각 서비스에 내려준다.
 *
 * <p>profile별 파일({@code {application}-{profile}.yml})을 함께 내려준다. 운영 값이 담기는
 * {@code config/*-prod.yml}은 git 추적 대상이 아니므로 배포 환경에서 직접 채운다.
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
