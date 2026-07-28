package org.coupon.productservice;

import org.coupon.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 상품 서비스. 상품 등록·조회와 재고(PRODUCT.stock)를 소유한다.
 *
 * <p>재고 차감은 이 프로젝트의 두 번째 동시성 지점이지만 DB 락을 걸지 않는다.
 * Kafka 커맨드의 파티션 키가 {@code productId}라 같은 상품의 차감이 한 파티션에서 직렬화되기 때문이다.
 * 이 전략은 "재고 변경 경로가 컨슈머 하나뿐"이라는 전제 위에 서 있어 관리자 재고 수정 API를 두지 않는다.
 *
 * <p>공통 응답·예외는 common 모듈의 {@code org.coupon.common.*}에 있어 컴포넌트 스캔 범위 밖이다.
 * 서블릿 전역 예외 처리를 활성화하려면 {@link GlobalExceptionHandler}를 명시적으로 등록해야 한다.
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class ProductServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductServiceApplication.class, args);
	}

}
