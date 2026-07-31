package org.coupon.couponservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.coupon.common.response.ApiResponse;
import org.coupon.couponservice.dto.CouponIssueAcceptedResponse;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.dto.IssuableCouponResponse;
import org.coupon.couponservice.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;

import java.util.List;

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

                    ### 처리 방식
                    Redis Lua 스크립트로 중복·수량을 원자적으로 검증해 자격을 확정한 뒤 Kafka로 발급을 비동기 처리한다.
                    202 응답 시점에 자격은 확정되지만 발급 이력 저장은 컨슈머가 뒤이어 수행한다.
                    그래서 201이 아니라 202다 — 응답 시점에 발급 건은 아직 만들어지지 않았다.
                    반영 여부는 `GET /api/v1/coupons/{couponId}`로 관찰한다.

                    ### 주의
                    - 재고 소진 시 409, 이미 발급받았으면 409, 종료된 이벤트면 409, 없는 쿠폰이면 404.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "발급 접수",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"couponId":1,"userId":42},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "쿠폰 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"COUPON_NOT_FOUND","errorMessage":"쿠폰을 찾을 수 없습니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 소진 · 중복 발급 · 종료된 이벤트",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"COUPON_SOLD_OUT","errorMessage":"쿠폰이 모두 소진되었습니다"}""")))
    })
    ResponseEntity<ApiResponse<CouponIssueAcceptedResponse>> issue(
            @Parameter(in = ParameterIn.PATH, description = "발급 대상 쿠폰 ID", required = true, example = "1")
            Long couponId,
            @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(
            summary = "발급 가능한 쿠폰 목록",
            description = """
                    검증된 토큰의 사용자가 지금 발급받을 수 있는 쿠폰만 반환한다. 세 조건을 모두 만족해야 한다.

                    - 이벤트가 진행 중이다 (`eventEndAt`이 미래)
                    - 재고가 남았다
                    - 이 사용자가 아직 받지 않았다

                    ### 주의
                    이 목록은 **조회 시점의 스냅샷**이다. 발급 이력은 Kafka로 비동기 반영되므로
                    방금 받은 쿠폰이 잠깐 목록에 남아 있을 수 있다. 그 상태에서 발급을 누르면 409가 나는데,
                    오류가 아니라 정상 흐름이다. 클라이언트는 409를 처리할 수 있어야 한다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":[{"couponId":1,"title":"신규가입 10% 할인","remaining":58,"totalQuantity":100,"discountType":"PERCENT","discountValue":10,"maxDiscountAmount":5000,"minOrderAmount":10000,"eventEndAt":"2026-12-31T23:59:59"}],"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}""")))
    })
    ResponseEntity<ApiResponse<List<IssuableCouponResponse>>> getIssuableCoupons(
            @Parameter(hidden = true) AuthenticatedUser user);

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
