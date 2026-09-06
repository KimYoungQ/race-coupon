package org.coupon.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.coupon.userservice.jwt.TokenBlacklistService;
import org.coupon.userservice.mapper.UserMapper;
import org.coupon.userservice.repository.RefreshTokenRedisRepository;
import org.coupon.userservice.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        log.info("회원가입 요청: username={}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException(request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .encodedPassword(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .build();
        User saved = userRepository.save(user);

        log.info("회원가입 완료: userId={}, username={}", saved.getId(), saved.getUsername());
        return userMapper.toSignupResponse(saved);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        log.info("로그인 요청: username={}", request.getUsername());

        authenticate(request.getUsername(), request.getPassword());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        refreshTokenRedisRepository.save(user.getId(), refreshToken, jwtTokenProvider.getRefreshTokenValidityMillis());

        log.info("로그인 완료: userId={}", user.getId());
        return userMapper.toLoginResponse(
                user,
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        jwtTokenProvider.validateToken(refreshToken);

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("리프레시 토큰이 아닙니다");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        log.info("토큰 재발급 요청: userId={}", userId);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        boolean rotated = refreshTokenRedisRepository.rotate(
                userId,
                refreshToken,
                newRefreshToken,
                jwtTokenProvider.getRefreshTokenValidityMillis());
        if (!rotated) {
            throw new InvalidTokenException("폐기된 토큰입니다");
        }

        log.info("토큰 재발급 완료: userId={}", userId);
        return TokenResponse.of(newAccessToken, newRefreshToken, jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    public void logout(String accessToken) {
        Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        Date expiry = jwtTokenProvider.getExpirationFromToken(accessToken);

        refreshTokenRedisRepository.deleteByUserId(userId);
        tokenBlacklistService.addToBlacklist(accessToken, expiry);

        log.info("로그아웃 완료: userId={}", userId);
    }

    private void authenticate(String username, String rawPassword) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, rawPassword));
        } catch (AuthenticationException e) {
            log.warn("로그인 실패: username={}", username);
            throw new InvalidCredentialsException();
        }
    }
}
