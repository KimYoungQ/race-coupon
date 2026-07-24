package org.coupon.couponservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.couponservice.service.CouponIssueService;
import org.coupon.couponservice.service.KafkaCouponIssueService;
import org.coupon.couponservice.service.PessimisticLockCouponIssueService;
import org.coupon.couponservice.security.AuthenticatedUser;
import org.coupon.couponservice.service.RedisCouponIssueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    /**
     * 발급 주체는 <b>이 서비스가 직접 검증한</b> 토큰의 {@code sub}에서만 얻는다.
     * 헤더나 쿼리 파라미터로 받으면 값을 적어 보내는 쪽이 주체를 정하게 되어 타인 명의 발급이 가능해진다.
     * 게이트웨이가 앞에서 한 번 걸러주지만, 우회 호출에 대비해 JwtAuthenticationFilter가 여기서도 재검증한다.
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> issue(
            @PathVariable Long couponId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "pessimistic") String strategy) {
        Long userId = user.getUserId();
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
