package org.coupon.gateway.config;

import org.coupon.security.JwtSecretProperties;
import org.coupon.security.JwtTokenContract;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import reactor.core.publisher.Mono;

/**
 * 게이트웨이의 1차 관문. JWT 서명·만료를 검증하고 공개 경로를 통과시킨다.
 *
 * <p><b>여기서 하지 않는 것: 권한(역할) 판정.</b> 인가는 각 서비스의
 * {@code @PreAuthorize}가 단일 출처다. 경로 패턴 기반 hasRole을 여기에도 두면
 * 규칙이 두 곳으로 갈라져 어느 쪽이 거부했는지 추적해야 한다.
 *
 * <p>검증에 성공한 요청의 {@code Authorization} 헤더는 <b>제거하지 않고 그대로</b> 다운스트림에
 * 넘긴다. 각 서비스가 토큰을 재검증해 SecurityContext를 채워야 게이트웨이를 우회한 직접 호출도
 * 막히고(2단 방어), 서비스가 요청 주체를 알 수 있다.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtSecretProperties.class)
public class SecurityConfig {

    /** 토큰 없이 통과시키는 경로. 위에서 아래로 평가되며 먼저 매칭된 규칙이 이긴다. */
    private static final String[] PERMIT_ALL = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/actuator/**",
            "/v3/api-docs/**",
    };

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // 브라우저에게 보내는 방어 지시. 게이트웨이 한 곳에서 설정하면 모든 서비스 응답에 적용된다.
                .headers(headers -> headers
                        // 클릭재킹: 우리 페이지를 투명 iframe으로 덮어 오조작을 유도하는 공격
                        .frameOptions(frame -> frame.mode(Mode.DENY))
                        // MIME 스니핑: 이미지로 올린 파일을 브라우저가 스크립트로 추측해 실행
                        .contentTypeOptions(Customizer.withDefaults())
                        // 정보 유출: 외부 링크 이동 시 토큰이 담긴 URL이 Referer로 새어나감
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        // XSS·데이터 유출: 주입된 스크립트가 외부로 데이터를 전송
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'")))

                // 토큰 기반 stateless 게이트웨이라 세션·폼로그인이 불필요하다.
                // CSRF는 브라우저가 인증정보를 자동 첨부하는 성질(쿠키 세션)을 전제로 하는데,
                // 여기서는 Authorization 헤더에 명시적으로 실어 보내므로 전제가 성립하지 않는다.
                // 단 토큰을 쿠키에 저장하도록 프론트를 바꾸면 이 판단을 다시 해야 한다.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 게이트웨이는 아이디/비밀번호 인증을 하지 않는다. 켜두면 ReactiveAuthenticationManager를
                // 요구해 필터 체인 생성이 실패하고, 인증 실패 시 브라우저 로그인 팝업도 뜬다.
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // 인증 없이 보호된 경로에 접근하면 리다이렉트가 아니라 401만 내려준다.
                .exceptionHandling(exception -> exception.authenticationEntryPoint(
                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))

                .authorizeExchange(exchange -> exchange
                        .pathMatchers(PERMIT_ALL).permitAll()
                        // 회원가입은 메서드로 갈라 연다. 경로 전체를 열면 같은 URL의
                        // GET(사용자 목록 조회)까지 인증 없이 노출된다.
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/signup").permitAll()
                        // 나머지는 "유효한 Access Token인가"까지만 본다. 역할 판정은 서비스가 한다.
                        .anyExchange().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))

                .build();
    }

    /**
     * HS256 대칭키 디코더. user-service와 동일한 비밀키를 공유한다.
     * 서명 검증과 함께 {@code exp} 만료 검증(JwtTimestampValidator)이 기본으로 적용된다.
     * Boot에는 HS256용 {@code spring.security.oauth2.resourceserver.*} 프로퍼티가 없어 빈으로 직접 등록한다.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(JwtSecretProperties jwtProperties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(jwtProperties.toSecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 기본 검증(exp 등)에 토큰 타입 검증을 더한다.
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(accessTokenTypeValidator()));
        return decoder;
    }

    /**
     * Access Token만 API 인증에 쓸 수 있게 막는다.
     * 근거는 {@link JwtTokenContract#TYPE_ACCESS} 참고. coupon-api는 같은 검사를
     * jjwt 기반 필터에서 수행하므로 구현 수단만 다르고 규칙은 동일하다.
     */
    private OAuth2TokenValidator<Jwt> accessTokenTypeValidator() {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN, "Access Token이 아닙니다", null);
        return jwt -> JwtTokenContract.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtTokenContract.CLAIM_TYPE))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }

    /**
     * {@code role} 클레임("ADMIN")을 {@code ROLE_ADMIN} 권한으로 매핑한다.
     * 기본 변환기는 {@code scope}/{@code scp} 클레임을 보므로 클레임명을 바꿔줘야 한다.
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenContract.CLAIM_ROLE);
        authorities.setAuthorityPrefix(JwtTokenContract.ROLE_PREFIX);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
