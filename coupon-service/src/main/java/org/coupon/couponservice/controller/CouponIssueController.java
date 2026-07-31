package org.coupon.couponservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.coupon.couponservice.dto.CouponIssueAcceptedResponse;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.security.AuthenticatedUser;
import org.coupon.couponservice.service.KafkaCouponIssueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponIssueController implements CouponIssueControllerApi {

    private final KafkaCouponIssueService kafkaCouponIssueService;

    /**
     * 발급 주체는 <b>이 서비스가 직접 검증한</b> 토큰의 {@code sub}에서만 얻는다.
     * 헤더나 쿼리 파라미터로 받으면 값을 적어 보내는 쪽이 주체를 정하게 되어 타인 명의 발급이 가능해진다.
     * 게이트웨이가 앞에서 한 번 걸러주지만, 우회 호출에 대비해 JwtAuthenticationFilter가 여기서도 재검증한다.
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponIssueAcceptedResponse>> issue(
            @PathVariable Long couponId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = user.getUserId();
        log.info("쿠폰 발급 요청: couponId={}, userId={}", couponId, userId);
        CouponIssueAcceptedResponse response = kafkaCouponIssueService.issue(couponId, userId);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> getCoupon(@PathVariable Long couponId) {
        return ResponseEntity.ok(ApiResponse.success(kafkaCouponIssueService.getCouponInfo(couponId)));
    }
}
