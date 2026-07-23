package org.coupon.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.userservice.domain.RefreshToken;
import org.coupon.userservice.domain.User;
import org.coupon.userservice.domain.UserRole;
import org.coupon.userservice.dto.request.LoginRequest;
import org.coupon.userservice.dto.request.SignupRequest;
import org.coupon.userservice.dto.request.TokenRefreshRequest;
import org.coupon.userservice.dto.response.LoginResponse;
import org.coupon.userservice.dto.response.SignupResponse;
import org.coupon.userservice.dto.response.TokenResponse;
import org.coupon.userservice.exception.DuplicateEmailException;
import org.coupon.userservice.exception.DuplicateUsernameException;
import org.coupon.userservice.exception.InvalidCredentialsException;
import org.coupon.userservice.exception.InvalidTokenException;
import org.coupon.userservice.exception.UserNotFoundException;
import org.coupon.userservice.jwt.JwtTokenProvider;
import org.coupon.userservice.repository.RefreshTokenRepository;
import org.coupon.userservice.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 회원가입·로그인·토큰 재발급을 담당한다.
 *
 * <p>비밀번호 검증은 직접 하지 않는다. AuthenticationManager에 위임해 UserDetails 조회와
 * PasswordEncoder.matches()가 Spring Security 표준 흐름을 타게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        log.info("회원가입 요청: username={}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException(request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        // 평문 비밀번호는 여기서 즉시 해시로 바뀌며, 이후 어디에도 남기지 않는다.
        User user = User.create(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                UserRole.USER);
        User saved = userRepository.save(user);

        log.info("회원가입 완료: userId={}, username={}", saved.getId(), saved.getUsername());
        return SignupResponse.of(saved);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("로그인 요청: username={}", request.getUsername());

        authenticate(request.getUsername(), request.getPassword());

        // 인증은 username 기준으로 끝났고, 토큰 클레임에 넣을 userId·role을 얻기 위해 도메인 User를 조회한다.
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        saveOrRotateRefreshToken(user.getId(), refreshToken);

        log.info("로그인 완료: userId={}", user.getId());
        return LoginResponse.of(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenValiditySeconds(),
                user.getUsername());
    }

    @Transactional
    public TokenResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        jwtTokenProvider.validateToken(refreshToken);

        // Access Token으로 무기한 재발급받는 경로를 막는다.
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("리프레시 토큰이 아닙니다");
        }

        // DB에 없다는 건 이미 rotate로 교체돼 무효화된 토큰이라는 뜻이다.
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("폐기된 토큰입니다"));

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        log.info("토큰 재발급 요청: userId={}", userId);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        stored.rotate(newRefreshToken, expiresAtOf(newRefreshToken));

        log.info("토큰 재발급 완료: userId={}", userId);
        return TokenResponse.of(newAccessToken, newRefreshToken, jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    /**
     * 인증 실패 원인을 InvalidCredentialsException 하나로 통일한다.
     * 아이디가 없는지 비밀번호가 틀렸는지 구분해 알려주면 계정 열거 공격의 단서가 된다.
     */
    private void authenticate(String username, String rawPassword) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, rawPassword));
        } catch (AuthenticationException e) {
            log.warn("로그인 실패: username={}", username);
            throw new InvalidCredentialsException();
        }
    }

    /**
     * Refresh Token은 사용자당 1건만 유지한다. 재로그인하면 기존 토큰을 교체해 즉시 무효화한다.
     */
    private void saveOrRotateRefreshToken(Long userId, String newToken) {
        LocalDateTime expiresAt = expiresAtOf(newToken);
        refreshTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        stored -> stored.rotate(newToken, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.create(userId, newToken, expiresAt)));
    }

    /**
     * 만료시각은 토큰 자체의 exp 클레임을 그대로 따른다. 별도로 계산하면 토큰과 DB가 어긋날 수 있다.
     */
    private LocalDateTime expiresAtOf(String token) {
        Date expiration = jwtTokenProvider.getExpirationFromToken(token);
        return expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
