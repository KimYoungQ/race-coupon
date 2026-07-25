package org.coupon.couponservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.coupon.common.response.ApiResponse;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;

/**
 * 선착순 쿠폰 발급·조회 API 문서화 인터페이스.
 *
 * <p>발급 주체는 클라이언트 입력이 아니라 <b>검증된 토큰의 sub</b>에서 얻으므로,
 * 인증 주체 파라미터는 {@code @Parameter(hidden = true)}로 문서에서 숨긴다.
 */
@Tag(name = "쿠폰", description = "선착순 쿠폰 발급 · 잔여 수량 조회 (JWT 필요)")
public interface CouponIssueControllerApi {

    @Operation(
            summary = "선착순 쿠폰 발급",
            description = """
                    검증된 토큰의 사용자에게 쿠폰을 1건 발급한다. 발급 주체는 요청 값이 아니라 토큰의 sub에서 얻는다.

                    ### strategy (동시성 전략)
                    - **none**: 제어 없음 (레이스 컨디션 재현용)
                    - **pessimistic**: 비관적 락 (기본값)
                    - **redis**: Redis INCR 카운팅
                    - **kafka**: Kafka 비동기 발급

                    ### 주의
                    - 재고 소진 시 409, 없는 쿠폰이면 404, 잘못된 전략이면 400.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "발급 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"couponId":1,"issuedQuantity":42,"remaining":58},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 전략",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_STRATEGY","errorMessage":"지원하지 않는 발급 전략입니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "쿠폰 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"COUPON_NOT_FOUND","errorMessage":"쿠폰을 찾을 수 없습니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 소진",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"COUPON_SOLD_OUT","errorMessage":"쿠폰이 모두 소진되었습니다"}""")))
    })
    ResponseEntity<ApiResponse<CouponIssueResponse>> issue(
            @Parameter(in = ParameterIn.PATH, description = "발급 대상 쿠폰 ID", required = true, example = "1")
            Long couponId,
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(in = ParameterIn.QUERY, description = "동시성 전략 (none·pessimistic·redis·kafka)", example = "pessimistic")
            String strategy);

    @Operation(
            summary = "쿠폰 잔여 수량 조회",
            description = "쿠폰의 발급 수량과 잔여 수량을 조회한다(관찰용).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"couponId":1,"issuedQuantity":42,"remaining":58},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "쿠폰 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"COUPON_NOT_FOUND","errorMessage":"쿠폰을 찾을 수 없습니다"}""")))
    })
    ResponseEntity<ApiResponse<CouponIssueResponse>> getCoupon(
            @Parameter(in = ParameterIn.PATH, description = "조회할 쿠폰 ID", required = true, example = "1")
            Long couponId);
}
