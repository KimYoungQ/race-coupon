package org.coupon.orderservice;

import org.coupon.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 주문 서비스. 주문 생성·조회를 담당하고, 이후 웨이브에서 재고 예약·쿠폰 사용 Saga의 오케스트레이터가 된다.
 *
 * <p>공통 응답·예외는 common 모듈의 {@code org.coupon.common.*}에 있어 컴포넌트 스캔 범위 밖이다.
 * 서블릿 전역 예외 처리를 활성화하려면 {@link GlobalExceptionHandler}를 명시적으로 등록해야 한다.
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
