package org.coupon.orderservice.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * {@link CouponClient} 를 서킷 브레이커로 감싼다. OrderService 는 이 클래스만 부른다.
 * <p>
 * 쿠폰 확인 호출은 세 가지로 끝난다.
 * <ul>
 *   <li>쿠폰 서비스가 4xx 로 답함(없는 쿠폰·이미 쓴 쿠폰 등) → {@link BusinessException} 이 그대로 전파된다. 서비스는 멀쩡하므로 실패로 세지 않는다.</li>
 *   <li>쿠폰 서비스가 응답하지 못함(연결 실패·타임아웃·5xx) → {@link FeignException}. 실패로 세고 503 으로 거절한다.</li>
 *   <li>실패가 쌓여 서킷이 열림 → 쿠폰 서비스를 두드리지 않고 {@link CallNotPermittedException}. 바로 503 으로 거절한다.</li>
 * </ul>
 * Resilience4j 는 예외 타입이 맞는 fallback 만 부르므로 BusinessException 용 fallback 은 두지 않는다.
 * 임계값은 설정의 resilience4j.circuitbreaker.instances.coupon-service 에 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponChecker {

    private final CouponClient couponClient;

    // 쿠폰 서비스가 응답하지 못하면(연결 실패·타임아웃·5xx) 서킷을 열어 이후 호출은 쿠폰 서비스를 두드리지 않고 바로 거절한다
    @CircuitBreaker(name = "coupon-service", fallbackMethod = "unavailable")
    public void checkUsable(Long couponId) {
        couponClient.checkUsable(couponId);
    }

    // 서킷이 열려 호출 자체가 막힌 경우
    private void unavailable(Long couponId, CallNotPermittedException e) {
        log.warn("쿠폰 서비스 서킷 열림, 호출 없이 거절: couponId={}", couponId);
        throw new BusinessException(ErrorCode.COUPON_SERVICE_UNAVAILABLE);
    }

    // 쿠폰 서비스가 응답하지 못한 경우 (연결 실패·타임아웃은 RetryableException 으로, FeignException 의 하위)
    private void unavailable(Long couponId, FeignException e) {
        log.warn("쿠폰 서비스 응답 실패: couponId={}, status={}, message={}", couponId, e.status(), e.getMessage());
        throw new BusinessException(ErrorCode.COUPON_SERVICE_UNAVAILABLE);
    }
}
