package org.coupon.userservice.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupResponse {

    private Long id;
    private String username;
    private String email;
}
