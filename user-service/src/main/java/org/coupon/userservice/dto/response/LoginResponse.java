package org.coupon.userservice.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 결과. expiresIn은 Access Token의 유효 기간(초)으로, 클라이언트가 만료 전에
 * 미리 재발급을 요청할 수 있도록 함께 내려준다.
 */
@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String username;

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, String username) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .username(username)
                .build();
    }
}
