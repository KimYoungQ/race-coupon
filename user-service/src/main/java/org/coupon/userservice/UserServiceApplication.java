package org.coupon.userservice;

import org.coupon.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 인증 서비스. 회원가입·로그인을 처리하고 JWT를 '발급'한다(검증은 apigateway 책임).
 * 게이트웨이와 동일한 HS256 비밀키(jwt.secret)를 공유해야 발급한 토큰이 통과된다.
 *
 * <p>공통 코드는 common 모듈의 org.coupon.common.* 패키지에 있어 컴포넌트 스캔 범위 밖이다.
 * 서블릿 전역 예외 처리를 위해 GlobalExceptionHandler만 명시적으로 등록한다.
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
