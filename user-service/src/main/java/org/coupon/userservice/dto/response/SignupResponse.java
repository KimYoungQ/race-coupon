package org.coupon.userservice.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.coupon.userservice.domain.User;

/**
 * 회원가입 결과. 비밀번호 해시는 노출 대상이 아니므로 담지 않는다.
 */
@Getter
@Builder
public class SignupResponse {

    private Long id;
    private String username;
    private String email;

    public static SignupResponse of(User user) {
        return SignupResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }
}
