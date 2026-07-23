package org.coupon.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Access Token 재발급 요청. 토큰 값 자체가 자격 증명이므로 로그에 남기지 않는다.
 */
@Getter
@NoArgsConstructor
public class TokenRefreshRequest {

    @NotBlank(message = "리프레시 토큰은 필수입니다")
    private String refreshToken;
}
