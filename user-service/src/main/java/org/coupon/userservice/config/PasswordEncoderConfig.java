package org.coupon.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder 전용 설정.
 *
 * <p>SecurityConfig 안에 두지 않고 별도 클래스로 분리한다. PasswordEncoder는 회원가입(Service)과
 * 인증(Security) 양쪽에서 쓰이는데, SecurityConfig에 두면 Service → SecurityConfig → 필터/서비스 →
 * 다시 Service 로 이어지는 순환 참조가 생길 수 있다. 의존이 없는 별도 설정으로 두면 이 고리가 끊긴다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
