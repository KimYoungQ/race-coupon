package org.coupon.racecoupon.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.racecoupon.common.ApiResponse;
import org.coupon.racecoupon.dto.CouponIssueResponse;
import org.coupon.racecoupon.exception.BusinessException;
import org.coupon.racecoupon.exception.ErrorCode;
import org.coupon.racecoupon.service.CouponIssueService;
import org.coupon.racecoupon.service.KafkaCouponIssueService;
import org.coupon.racecoupon.service.PessimisticLockCouponIssueService;
import org.coupon.racecoupon.service.RedisCouponIssueService;
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
    private final PessimisticLockCouponIssueService pessimisticLockCouponIssueService;
    private final RedisCouponIssueService redisCouponIssueService;
    private final KafkaCouponIssueService kafkaCouponIssueService;

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> issue(
            @PathVariable Long couponId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "pessimistic") String strategy) {
        log.info("쿠폰 발급 요청: couponId={}, userId={}, strategy={}", couponId, userId, strategy);
        CouponIssueResponse response = switch (strategy) {
            case "none" -> couponIssueService.issue(couponId, userId);
            case "pessimistic" -> pessimisticLockCouponIssueService.issue(couponId, userId);
            case "redis" -> redisCouponIssueService.issue(couponId, userId);
            case "kafka" -> kafkaCouponIssueService.issue(couponId, userId);
            default -> throw new BusinessException(ErrorCode.INVALID_STRATEGY, "지원하지 않는 전략입니다: " + strategy);
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> getCoupon(@PathVariable Long couponId) {
        return ResponseEntity.ok(ApiResponse.success(couponIssueService.getCouponInfo(couponId)));
    }
}
