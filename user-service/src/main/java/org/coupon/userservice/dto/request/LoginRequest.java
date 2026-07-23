package org.coupon.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 요청. 자격 증명 검증은 AuthenticationManager가 수행하므로 여기서는 값 존재 여부만 검증한다.
 */
@Getter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "아이디는 필수입니다")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다")
    private String password;
}
