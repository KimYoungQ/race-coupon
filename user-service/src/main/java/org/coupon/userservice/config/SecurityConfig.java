package org.coupon.userservice.config;

import lombok.RequiredArgsConstructor;
import org.coupon.userservice.jwt.JwtAuthenticationEntryPoint;
import org.coupon.userservice.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT 기반 무상태 인증 설정.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /**
     * AuthService가 로그인 시 authenticate()를 호출하려면 AuthenticationManager가 빈이어야 한다.
     * Spring Security 6부터는 자동으로 노출되지 않으므로 이렇게 직접 꺼내 등록한다.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // JWT는 Authorization 헤더로 보낸다. 쿠키 자동 전송이 없으니 CSRF 공격 대상이 아니다.
                .csrf(csrf -> csrf.disable())
                // 서버에 세션을 두지 않는다. 인증 상태는 매 요청의 토큰이 결정한다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 구체적인 규칙이 먼저, 포괄 규칙(anyRequest)이 나중. 순서가 뒤집히면 로그인 API조차 인증을 요구한다.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                // 인증 실패 시 로그인 폼으로 리다이렉트하지 않고 401 JSON을 내려주기 위한 진입점.
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // 폼 로그인 필터보다 먼저 토큰을 읽어 SecurityContext를 채운다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
