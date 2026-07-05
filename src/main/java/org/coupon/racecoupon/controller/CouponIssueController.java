package org.coupon.racecoupon.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.racecoupon.common.ApiResponse;
import org.coupon.racecoupon.dto.CouponIssueResponse;
import org.coupon.racecoupon.service.CouponIssueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponIssueController {

    private final CouponIssueService couponIssueService;

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> issue(@PathVariable Long couponId,
                                                                  @RequestParam Long userId) {
        log.info("쿠폰 발급 요청: couponId={}, userId={}", couponId, userId);
        CouponIssueResponse response = couponIssueService.issue(couponId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> getCoupon(@PathVariable Long couponId) {
        return ResponseEntity.ok(ApiResponse.success(couponIssueService.getCouponInfo(couponId)));
    }
}
