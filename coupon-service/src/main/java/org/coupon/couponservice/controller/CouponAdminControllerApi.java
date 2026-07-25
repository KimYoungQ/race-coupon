package org.coupon.couponservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.coupon.common.response.ApiResponse;
import org.coupon.couponservice.dto.CouponCreateRequest;
import org.coupon.couponservice.dto.CouponResponse;
import org.springframework.http.ResponseEntity;

/**
 * 관리자 쿠폰 등록 API 문서화 인터페이스.
 *
 * <p>{@code ADMIN} 역할이 필요하다. 권한이 없으면 403이며, 인가는 컨트롤러의
 * {@code @PreAuthorize("hasRole('ADMIN')")}가 단독으로 판정한다.
 */
@Tag(name = "쿠폰 관리(관리자)", description = "쿠폰 등록 API — ROLE_ADMIN 필요")
public interface CouponAdminControllerApi {

    @Operation(
            summary = "쿠폰 등록",
            description = """
                    새 쿠폰을 등록한다. **관리자(ROLE_ADMIN) 전용.**

                    ### 할인 유형(discountType)
                    - **PERCENT**: 정률(%), 할인 값 1~100
                    - **FIXED_AMOUNT**: 정액(원 차감), 할인 값 1 이상

                    ### 선택 항목
                    - **maxDiscountAmount**: 최대 할인 한도(원). 생략 시 한도 없음.
                    - **minOrderAmount**: 최소 주문 금액(원). 생략 시 조건 없음.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"couponId":1,"title":"신규가입 10% 할인","totalQuantity":100,"issuedQuantity":0,"discountType":"PERCENT","discountValue":10,"maxDiscountAmount":5000,"minOrderAmount":10000},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 할인 값 범위 위반",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_DISCOUNT","errorMessage":"할인 정보가 올바르지 않습니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"FORBIDDEN","errorMessage":"접근 권한이 없습니다"}""")))
    })
    ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @RequestBody(description = "쿠폰 등록 요청", required = true,
                    content = @Content(schema = @Schema(implementation = CouponCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {"title":"신규가입 10% 할인","totalQuantity":100,"discountType":"PERCENT","discountValue":10,"maxDiscountAmount":5000,"minOrderAmount":10000}""")))
            @Valid CouponCreateRequest request);
}
