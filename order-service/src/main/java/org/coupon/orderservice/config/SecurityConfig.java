package org.coupon.orderservice.config;

import lombok.RequiredArgsConstructor;
import org.coupon.common.security.JwtSecretProperties;
import org.coupon.orderservice.jwt.JwtAuthenticationEntryPoint;
import org.coupon.orderservice.jwt.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 게이트웨이가 이미 검증한 토큰을 <b>한 번 더</b> 검증한다(2단 방어).
 *
 * <p>중복처럼 보이지만 목적이 둘이다.
 * <ul>
 *     <li>게이트웨이를 건너뛰고 이 서비스를 직접 호출하는 경로를 막는다.</li>
 *     <li>주문 주체를 복원한다. 주문자는 검증된 토큰의 {@code sub}에서만 얻는다 —
 *         클라이언트가 적어 보낸 값을 신뢰하면 타인 명의 주문이 가능해진다.</li>
 * </ul>
 *
 * <p>역할 판정은 여기서 하지 않고 {@code @PreAuthorize}가 메서드마다 선언한다.
 * 경로 패턴과 애노테이션 두 곳에 규칙을 두면 어느 쪽이 거부했는지 추적해야 한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtSecretProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
