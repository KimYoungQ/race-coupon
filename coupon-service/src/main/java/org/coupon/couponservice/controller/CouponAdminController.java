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

@Slf4j
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminController implements CouponAdminControllerApi {

    private final CouponAdminService couponAdminService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CouponCreateRequest request) {
        log.info("쿠폰 등록 요청: title={}", request.title());
        CouponResponse response = couponAdminService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
