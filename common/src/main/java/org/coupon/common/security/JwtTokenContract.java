package org.coupon.common.security;

/**
 * 토큰을 해석하는 규칙의 단일 출처. user-service가 발급한 토큰을 게이트웨이와 coupon-api가
 * <b>똑같이</b> 읽어야 하므로 클레임명과 권한 접두사를 여기 모은다.
 *
 * <p>두 서비스는 검증 수단이 다르다 — 게이트웨이는 {@code oauth2ResourceServer}(Nimbus),
 * coupon-api는 jjwt 기반 필터다. 수단이 달라도 <b>클레임 이름과 의미는 같아야</b> 하고,
 * 어긋나면 「서명은 맞는데 한쪽에서만 401」이라는 원인 찾기 어려운 장애가 난다.
 *
 * <p>토큰 클레임 계약(발급 측은 user-service {@code JwtTokenProvider}):
 * {@code sub}=userId, {@code username}, {@code role}(ROLE_ 접두사 없는 역할명 하나),
 * {@code type}=access|refresh, {@code iat}, {@code exp}.
 */
public final class JwtTokenContract {

    /** 권한이 담기는 클레임. 값은 "ADMIN"처럼 접두사 없는 역할명 하나다. */
    public static final String CLAIM_ROLE = "role";

    /** 사용자명 클레임. 식별자는 sub이고 이건 표시용이다. */
    public static final String CLAIM_USERNAME = "username";

    /** 토큰 용도 클레임. access | refresh */
    public static final String CLAIM_TYPE = "type";

    /**
     * API 인증에 쓸 수 있는 유일한 토큰 타입.
     *
     * <p>Refresh Token도 같은 키로 서명되므로 타입 검사가 없으면 서명·만료 검사를 그대로 통과한다.
     * 그 경우 수명이 30분인 Access Token 대신 7일짜리 Refresh Token으로 API를 호출할 수 있게 되어,
     * 토큰이 탈취됐을 때 공격 가능 시간이 30분에서 7일로 늘어난다.
     */
    public static final String TYPE_ACCESS = "access";

    /** Spring Security의 hasRole(...)과 @PreAuthorize가 요구하는 권한 접두사. */
    public static final String ROLE_PREFIX = "ROLE_";

    private JwtTokenContract() {
    }
}
