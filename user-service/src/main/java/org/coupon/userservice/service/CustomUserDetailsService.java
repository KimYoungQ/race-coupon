package org.coupon.userservice.service;

import lombok.RequiredArgsConstructor;
import org.coupon.userservice.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * DB의 사용자를 Spring Security가 이해하는 UserDetails로 변환한다.
 *
 * <p>비밀번호 비교는 여기서 하지 않는다. DaoAuthenticationProvider가 여기서 돌려준 해시와
 * 입력값을 PasswordEncoder.matches()로 대조한다.
 *
 * <p>import한 User는 Spring Security의 UserDetails 구현체이며, 도메인 엔티티
 * {@code org.coupon.userservice.domain.User}와 다른 타입이다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 프로젝트의 BusinessException으로 바꾸지 않는다. Spring Security가 이 타입을 잡아
        // BadCredentialsException으로 변환해야 "아이디 없음"과 "비밀번호 틀림"이 응답에서 구분되지 않는다.
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return User.builder()
                .username(user.getUsername())
                // DB에 저장된 BCrypt 해시를 그대로 넘긴다.
                .password(user.getPassword())
                // roles()는 내부적으로 ROLE_ 접두사를 붙인다(authorities()와 다름).
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}
