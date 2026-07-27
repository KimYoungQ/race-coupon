package org.coupon.userservice.mapper;

import org.coupon.userservice.domain.User;
import org.coupon.userservice.dto.response.LoginResponse;
import org.coupon.userservice.dto.response.SignupResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * User Entity → Response DTO 변환. 구현체는 컴파일 시점에 생성된다.
 * Entity 생성은 인코딩된 비밀번호를 요구하는 {@link User}의 빌더가 담당하므로 여기서 다루지 않는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    SignupResponse toSignupResponse(User user);

    /**
     * 토큰은 엔티티가 아니라 JwtTokenProvider가 만든 값이라 별도 인자로 받아 함께 매핑한다.
     */
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "expiresIn", source = "expiresIn")
    LoginResponse toLoginResponse(User user, String accessToken, String refreshToken, long expiresIn);
}
