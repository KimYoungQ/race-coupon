package org.coupon.userservice.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 토큰 재발급 결과. Refresh Token도 함께 교체(rotate)되므로 클라이언트는 이 값으로 갱신해야 한다.
 */
@Getter
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .build();
    }
}
