package org.coupon.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.coupon.productservice.dto.ProductCreateRequest;
import org.coupon.productservice.dto.ProductResponse;
import org.coupon.productservice.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상품 등록·조회 API.
 *
 * <p>재고를 바꾸는 엔드포인트는 의도적으로 없다. 재고 변경 경로가 둘이 되면
 * Kafka 파티션 키({@code productId})로 직렬화를 보장하는 전략이 무너진다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController implements ProductControllerApi {

    private final ProductService productService;

    /**
     * {@code hasRole('ADMIN')}은 {@code ROLE_ADMIN} 권한을 요구한다.
     * 토큰의 {@code role} 클레임("ADMIN")에 ROLE_ 접두사를 붙이는 일은
     * {@code AuthenticatedUser.from(...)}이 담당한다.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        log.info("상품 등록 요청: name={}", request.name());
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(productId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts() {
        return ResponseEntity.ok(ApiResponse.success(productService.getProducts()));
    }
}
