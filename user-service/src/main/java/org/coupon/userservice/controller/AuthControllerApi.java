package org.coupon.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.coupon.common.response.ApiResponse;
import org.coupon.userservice.dto.request.LoginRequest;
import org.coupon.userservice.dto.request.SignupRequest;
import org.coupon.userservice.dto.request.TokenRefreshRequest;
import org.coupon.userservice.dto.response.LoginResponse;
import org.coupon.userservice.dto.response.SignupResponse;
import org.coupon.userservice.dto.response.TokenResponse;
import org.springframework.http.ResponseEntity;

/**
 * 인증 API 문서화 인터페이스. Swagger 애노테이션을 여기에 모아 컨트롤러를 깨끗하게 유지한다.
 *
 * <p>세 엔드포인트 모두 토큰 없이 호출하므로 {@code @SecurityRequirements}(빈 값)로
 * 전역 bearerAuth 요구를 해제한다 — UI에 잠금 아이콘이 뜨지 않는다.
 */
@Tag(name = "인증", description = "회원가입 · 로그인 · 토큰 재발급 (토큰 없이 호출)")
public interface AuthControllerApi {

    @Operation(
            summary = "회원가입",
            description = """
                    새 사용자를 등록한다.

                    ### 검증 규칙
                    - **username**: 필수, 4~20자
                    - **email**: 필수, 이메일 형식
                    - **password**: 필수, 8~64자 (평문으로 받아 서버에서 즉시 BCrypt 해시)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"id":1,"username":"couponuser","email":"user@example.com"},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_INPUT","errorMessage":"비밀번호는 8자 이상 64자 이하여야 합니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "아이디 또는 이메일 중복",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"DUPLICATE_USERNAME","errorMessage":"이미 사용 중인 아이디입니다"}""")))
    })
    @SecurityRequirements
    ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody(description = "회원가입 요청", required = true,
                    content = @Content(schema = @Schema(implementation = SignupRequest.class),
                            examples = @ExampleObject(value = """
                                    {"username":"couponuser","email":"user@example.com","password":"Password1!"}""")))
            @Valid SignupRequest request);

    @Operation(
            summary = "로그인",
            description = """
                    자격 증명을 검증하고 JWT를 발급한다.

                    ### 응답 토큰
                    - **accessToken**: API 호출용 (수명 30분)
                    - **refreshToken**: 재발급용 (수명 7일)
                    - **expiresIn**: accessToken 유효 시간(초)

                    발급받은 accessToken은 우측 상단 **Authorize**에 넣으면 보호 API를 호출할 수 있다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"accessToken":"eyJhbGciOiJIUzI1NiJ9...","refreshToken":"eyJhbGciOiJIUzI1NiJ9...","expiresIn":1800,"username":"couponuser"},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_INPUT","errorMessage":"아이디는 필수입니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_CREDENTIALS","errorMessage":"아이디 또는 비밀번호가 올바르지 않습니다"}""")))
    })
    @SecurityRequirements
    ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody(description = "로그인 요청", required = true,
                    content = @Content(schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(value = """
                                    {"username":"couponuser","password":"Password1!"}""")))
            @Valid LoginRequest request);

    @Operation(
            summary = "토큰 재발급",
            description = """
                    Refresh Token으로 새 Access/Refresh Token을 발급한다.

                    ### 동작
                    - 기존 Refresh Token은 교체(rotate)되어 즉시 무효화된다.
                    - 만료되거나 이미 교체된 Refresh Token으로는 재발급할 수 없다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"accessToken":"eyJhbGciOiJIUzI1NiJ9...","refreshToken":"eyJhbGciOiJIUzI1NiJ9...","expiresIn":1800},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_INPUT","errorMessage":"리프레시 토큰은 필수입니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "만료·위조·폐기된 토큰",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_TOKEN","errorMessage":"유효하지 않은 토큰입니다"}""")))
    })
    @SecurityRequirements
    ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody(description = "재발급 요청 (Refresh Token)", required = true,
                    content = @Content(schema = @Schema(implementation = TokenRefreshRequest.class),
                            examples = @ExampleObject(value = """
                                    {"refreshToken":"eyJhbGciOiJIUzI1NiJ9..."}""")))
            @Valid TokenRefreshRequest request);
}
