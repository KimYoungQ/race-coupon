package org.coupon.productservice.controller;

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
import org.coupon.productservice.dto.ProductCreateRequest;
import org.coupon.productservice.dto.ProductResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 상품 API 문서화 인터페이스.
 *
 * <p>등록은 {@code ADMIN} 역할이 필요하고, 인가는 컨트롤러의
 * {@code @PreAuthorize("hasRole('ADMIN')")}가 단독으로 판정한다.
 */
@Tag(name = "상품", description = "상품 등록(관리자) · 조회 API (JWT 필요)")
public interface ProductControllerApi {

    @Operation(
            summary = "상품 등록",
            description = """
                    새 상품을 등록한다. **관리자(ROLE_ADMIN) 전용.**

                    ### 재고
                    재고 초기값은 이 API에서만 정한다. 등록 이후 재고를 바꾸는 경로는
                    주문 Saga의 재고 예약/복구 커맨드뿐이며, 관리자 재고 수정 API는 없다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"productId":1,"name":"기계식 키보드","price":89000,"stock":100,"createdAt":"2026-07-27T10:00:00"},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"INVALID_INPUT","errorMessage":"name: 상품명은 필수입니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음·만료·위조",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"UNAUTHORIZED","errorMessage":"인증이 필요합니다"}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"FORBIDDEN","errorMessage":"접근 권한이 없습니다"}""")))
    })
    ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestBody(description = "상품 등록 요청", required = true,
                    content = @Content(schema = @Schema(implementation = ProductCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {"name":"기계식 키보드","price":89000,"stock":100}""")))
            @Valid ProductCreateRequest request);

    @Operation(
            summary = "상품 단건 조회",
            description = "상품의 이름·가격·잔여 재고를 조회한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":{"productId":1,"name":"기계식 키보드","price":89000,"stock":58,"createdAt":"2026-07-27T10:00:00"},"errorCode":null,"errorMessage":null}"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"data":null,"errorCode":"PRODUCT_NOT_FOUND","errorMessage":"상품을 찾을 수 없습니다: 999"}""")))
    })
    ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @Parameter(in = ParameterIn.PATH, description = "조회할 상품 ID", required = true, example = "1")
            Long productId);

    @Operation(
            summary = "상품 목록 조회",
            description = "등록된 상품 전체를 조회한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"data":[{"productId":1,"name":"기계식 키보드","price":89000,"stock":58,"createdAt":"2026-07-27T10:00:00"}],"errorCode":null,"errorMessage":null}""")))
    })
    ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts();
}
