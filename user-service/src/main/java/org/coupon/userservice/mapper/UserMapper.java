package org.coupon.userservice.mapper;

import org.coupon.userservice.domain.User;
import org.coupon.userservice.dto.response.LoginResponse;
import org.coupon.userservice.dto.response.SignupResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    SignupResponse toSignupResponse(User user);

    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "expiresIn", source = "expiresIn")
    LoginResponse toLoginResponse(User user, String accessToken, String refreshToken, long expiresIn);
}
