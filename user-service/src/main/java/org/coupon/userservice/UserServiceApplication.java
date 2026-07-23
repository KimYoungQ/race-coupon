package org.coupon.userservice;

import org.coupon.racecoupon.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 인증 서비스. 회원가입·로그인을 처리하고 JWT를 '발급'한다(검증은 gateway-service 책임).
 * 게이트웨이와 동일한 HS256 비밀키(jwt.secret)를 공유해야 발급한 토큰이 통과된다.
 *
 * <p>공통 코드는 coupon-core의 org.coupon.racecoupon.* 패키지에 있어 컴포넌트 스캔 범위 밖이다.
 * GlobalExceptionHandler만 명시적으로 등록한다. 반대로 @EntityScan은 추가하지 않는다 —
 * 기본 스캔 범위를 유지해야 Coupon·IssuedCoupon 엔티티가 user_service 스키마에 생기지 않는다.
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
