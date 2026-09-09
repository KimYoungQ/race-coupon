package org.coupon.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// name 은 Eureka 에 등록된 서비스 이름. Feign 이 로드밸런서를 거쳐 실제 주소로 바꿔 준다
@FeignClient(name = "coupon-service")
public interface CouponClient {

    /**
     * 쿠폰 서비스에 "이 사용자가 지금 쓸 수 있는 쿠폰인지" 묻는다.
     * 쓸 수 없으면 {@link ApiResponseErrorDecoder} 가 쿠폰 서비스의 에러 코드 그대로 BusinessException 을 던진다.
     */
    @GetMapping("/api/v1/coupons/{couponId}/usable")
    void checkUsable(@PathVariable("couponId") Long couponId);
}
