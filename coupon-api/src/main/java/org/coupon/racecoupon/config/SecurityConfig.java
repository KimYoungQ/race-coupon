package org.coupon.racecoupon.config;

import lombok.RequiredArgsConstructor;
import org.coupon.racecoupon.jwt.JwtAuthenticationEntryPoint;
import org.coupon.racecoupon.jwt.JwtAuthenticationFilter;
import org.coupon.security.JwtSecretProperties;
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
 *     <li>게이트웨이를 건너뛰고 이 서비스를 직접 호출하는 경로를 막는다.
 *         재검증이 없으면 내부망에 닿을 수 있는 누구나 인증 없이 쿠폰을 발급할 수 있다.</li>
 *     <li>발급 주체를 복원한다. 요청자는 검증된 토큰의 {@code sub}에서만 얻는다 —
 *         클라이언트가 적어 보낸 헤더를 신뢰하면 타인 명의 발급이 가능해진다.</li>
 * </ul>
 *
 * <p>역할 판정은 여기서 하지 않고 {@code @PreAuthorize}가 메서드마다 선언한다.
 * 경로 패턴과 애노테이션 두 곳에 규칙을 두면 어느 쪽이 거부했는지 추적해야 한다.
 */
@Configuration
@EnableWebSecurity
// @PreAuthorize를 켜는 스위치. 빠뜨리면 애노테이션이 예외 없이 조용히 무시돼
// 관리자 전용 API가 전원에게 열린다.
@EnableMethodSecurity
// JwtSecretProperties는 org.coupon.security 패키지라 컴포넌트 스캔 범위 밖이다.
@EnableConfigurationProperties(JwtSecretProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 토큰을 Authorization 헤더로 받는다. 브라우저가 자동 첨부하지 않으므로 CSRF 전제가 없다.
                .csrf(csrf -> csrf.disable())
                // 인증 상태는 매 요청의 토큰이 결정한다. 서버에 세션을 두지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 구체적인 규칙이 먼저, 포괄 규칙(anyRequest)이 나중.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // 여기까지는 "유효한 Access Token인가"만 본다. 권한은 @PreAuthorize가 본다.
                        .anyRequest().authenticated())
                // 인증 실패 시 빈 403 대신 401 + ApiResponse 본문을 내려주기 위한 진입점.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // 폼 로그인 필터보다 먼저 토큰을 읽어 SecurityContext를 채운다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
