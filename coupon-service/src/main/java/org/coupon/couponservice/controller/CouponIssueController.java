package org.coupon.couponservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.coupon.couponservice.dto.CouponIssueAcceptedResponse;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.dto.IssuableCouponResponse;
import org.coupon.couponservice.metrics.CouponIssueMetrics;
import org.coupon.couponservice.security.AuthenticatedUser;
import org.coupon.couponservice.service.CouponService;
import org.coupon.couponservice.service.CouponIssueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponIssueController implements CouponIssueControllerApi {

    private final CouponIssueService couponIssueService;
    private final CouponService couponService;
    private final CouponIssueMetrics metrics;

    @GetMapping("/issuable")
    public ResponseEntity<ApiResponse<List<IssuableCouponResponse>>> getIssuableCoupons(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(couponService.findIssuable(user.getUserId())));
    }

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponIssueAcceptedResponse>> issue(
            @PathVariable Long couponId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = user.getUserId();
        metrics.requested();
        log.info("쿠폰 발급 요청: couponId={}, userId={}", couponId, userId);
        CouponIssueAcceptedResponse response = couponIssueService.issue(couponId, userId);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> getCoupon(@PathVariable Long couponId) {
        return ResponseEntity.ok(ApiResponse.success(couponIssueService.getCouponInfo(couponId)));
    }
}
