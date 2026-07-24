package org.coupon.couponservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.coupon.couponservice.dto.CouponCreateRequest;
import org.coupon.couponservice.dto.CouponResponse;
import org.coupon.couponservice.service.CouponAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 쿠폰 API.
 *
 * <p>권한은 게이트웨이 경로 규칙이 아니라 여기 {@code @PreAuthorize}가 선언한다.
 * 규칙이 API 바로 옆에 있어야 새 엔드포인트를 추가할 때 인가를 빠뜨리지 않는다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminController {

    private final CouponAdminService couponAdminService;

    /**
     * {@code hasRole('ADMIN')}은 {@code ROLE_ADMIN} 권한을 요구한다.
     * 토큰의 {@code role} 클레임("ADMIN")에 ROLE_ 접두사를 붙이는 일은
     * {@code JwtTokenContract.grantedAuthoritiesConverter()}가 담당한다.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CouponCreateRequest request) {
        log.info("쿠폰 등록 요청: title={}", request.title());
        CouponResponse response = couponAdminService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
