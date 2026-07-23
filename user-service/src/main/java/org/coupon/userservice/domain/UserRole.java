package org.coupon.userservice.domain;

/**
 * 사용자 권한. JWT의 role 클레임에 이름 그대로 실린다(예: "ADMIN").
 * ROLE_ 접두사는 붙이지 않는다 — 게이트웨이가 검증 시 ROLE_을 붙여 GrantedAuthority로 매핑한다.
 */
public enum UserRole {
    USER,
    ADMIN
}
