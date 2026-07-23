package org.coupon.racecoupon.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.coupon.security.JwtTokenContract;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 검증된 토큰에서 복원한 요청 주체. 컨트롤러가 {@code @AuthenticationPrincipal}로 받는다.
 *
 * <p>Spring Security 기본 {@code User}를 쓰지 않는 이유는 <b>userId를 담을 자리가 없어서</b>다.
 * {@code UserDetails}는 {@code getUsername()}만 제공하는데 쿠폰 발급은 숫자 식별자를 쓴다.
 * 기본 구현으로 가면 principal에서 userId가 유실돼 요청 속성 같은 우회로가 필요해진다.
 *
 * <p>비밀번호는 담지 않는다. 토큰 인증이라 대조할 일이 없고, 굳이 실어두면 유출 경로만 늘어난다.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser implements UserDetails {

    /** JWT의 sub. 발급 이력(IssuedCoupon)에 기록되는 식별자다. */
    private final Long userId;

    private final String username;

    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 토큰 클레임을 그대로 받아 주체를 만든다.
     *
     * <p>{@code role}은 접두사 없는 역할명("ADMIN")이다. {@code hasRole('ADMIN')}과
     * {@code @PreAuthorize}가 요구하는 {@code ROLE_} 접두사는 <b>여기서만</b> 붙인다 —
     * 호출부마다 붙이면 한 곳을 빠뜨렸을 때 그 경로만 403이 나고 원인을 찾기 어렵다.
     *
     * @param role 접두사 없는 역할명. null이면 권한 없는 주체가 된다
     */
    public static AuthenticatedUser from(Long userId, String username, String role) {
        List<GrantedAuthority> authorities = role == null || role.isBlank()
                ? List.of()
                : List.of(new SimpleGrantedAuthority(JwtTokenContract.ROLE_PREFIX + role));
        return new AuthenticatedUser(userId, username, authorities);
    }

    /** 토큰 인증에서는 비밀번호를 대조하지 않는다. UserDetails 계약을 채우기 위한 빈 값. */
    @Override
    public String getPassword() {
        return "";
    }
}
