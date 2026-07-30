package org.coupon.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.coupon.common.response.ApiResponse;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.dto.OrderResponse;
import org.coupon.orderservice.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 주문 생성·조회 API 문서화 인터페이스.
 *
 * <p>주문 주체는 클라이언트 입력이 아니라 <b>검증된 토큰의 sub</b>에서 얻으므로,
 * 인증 주체 파라미터는 {@code @Parameter(hidden = true)}로 문서에서 숨긴다.
 */
@Tag(name = "주문", description = "주문 생성 · 내 주문 조회 (JWT 필요)")
public interface OrderControllerApi {

    @Operation(
            summary = "주문 생성",
            description = """
                    주문을 접수한다. 주문자는 요청 값이 아니라 토큰의 sub에서 얻는다.

                    ### 응답이 201이 아니라 202인 이유
                    이 시점에 확정된 것은 주문 접수 사실(ORDERS 한 줄)뿐이다.
                    재고 예약과 쿠폰 사용은 뒤이어 비동기로 처리되며 실패하면 주문은 FAILED가 된다.
                    최종 결과는 응답의 orderId로 단건 조회해 확인한다.

                    ### 상태 흐름
                    `CREATED` → `STOCK_RESERVED` → `COMPLETED`,
                    실패 시 `COMPENSATING` → `FAILED` 또는 곧바로 `FAILED`.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "주문 접수 (처리는 비동기로 계속)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"orderId":1,"status":"CREATED"},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_INPUT","errorMessage":"수량은 1 이상이어야 합니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}""")))
    })
    ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
            @RequestBody(description = "주문 생성 요청", required = true,
                    content = @Content(schema = @Schema(implementation = OrderCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {"productId":1,"quantity":2,"couponId":10}""")))
            @Valid OrderCreateRequest request,
            @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(
            summary = "내 주문 목록 조회",
            description = "토큰의 sub에 해당하는 주문만 최신순으로 조회한다. 다른 사용자의 주문은 포함되지 않는다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":[{"orderId":1,"status":"COMPLETED","couponId":10,"totalAmount":20000,"discountAmount":2000,"finalAmount":18000,"failureCode":null,"createdAt":"2026-07-27T10:00:00","updatedAt":"2026-07-27T10:00:02","items":[{"productId":1,"productName":"무선 이어폰","unitPrice":10000,"quantity":2}]}],"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}""")))
    })
    ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(
            summary = "내 주문 단건 조회",
            description = """
                    주문 하나의 현재 상태와 확정 금액을 조회한다. 비동기 처리 결과를 확인하는 경로다.

                    **다른 사용자의 주문은 403이 아니라 404다.** 403을 주면 그 orderId가 존재한다는 사실이 새어
                    남의 주문 ID를 열거할 수 있다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"orderId":1,"status":"CREATED","couponId":null,"totalAmount":0,"discountAmount":0,"finalAmount":0,"failureCode":null,"createdAt":"2026-07-27T10:00:00","updatedAt":"2026-07-27T10:00:00","items":[{"productId":1,"productName":null,"unitPrice":null,"quantity":2}]},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문 없음 또는 내 주문이 아님",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"ORDER_NOT_FOUND","errorMessage":"주문을 찾을 수 없습니다: 1"}""")))
    })
    ResponseEntity<ApiResponse<OrderResponse>> getMyOrder(
            @Parameter(in = ParameterIn.PATH, description = "조회할 주문 ID", required = true, example = "1")
            Long orderId,
            @Parameter(hidden = true) AuthenticatedUser user);
}
