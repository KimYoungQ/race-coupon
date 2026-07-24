package org.coupon.couponservice;

import org.coupon.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 쿠폰 발급 서비스. 동시성 전략(무제어/비관적 락/Redis/Kafka)과 발급 API,
 * 그리고 Kafka 발급 메시지 소비(IssuedCoupon 영속화)까지 한 모듈에서 담당한다.
 *
 * <p>공통 응답·예외는 common 모듈의 {@code org.coupon.common.*}에 있어 컴포넌트 스캔 범위 밖이다.
 * 서블릿 전역 예외 처리를 활성화하려면 {@link GlobalExceptionHandler}를 명시적으로 등록해야 한다.
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class CouponServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CouponServiceApplication.class, args);
	}

}
