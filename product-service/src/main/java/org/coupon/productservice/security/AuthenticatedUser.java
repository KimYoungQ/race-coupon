package org.coupon.productservice.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.coupon.common.security.JwtTokenContract;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@AllArgsConstructor
public class AuthenticatedUser implements UserDetails {

    private final Long userId;

    private final String username;

    private final Collection<? extends GrantedAuthority> authorities;

    public static AuthenticatedUser from(Long userId, String username, String role) {
        List<GrantedAuthority> authorities = role == null || role.isBlank()
                ? List.of()
                : List.of(new SimpleGrantedAuthority(JwtTokenContract.ROLE_PREFIX + role));
        return new AuthenticatedUser(userId, username, authorities);
    }

    public String getPassword() {
        return "";
    }
}
