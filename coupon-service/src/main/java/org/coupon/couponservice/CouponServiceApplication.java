package org.coupon.couponservice;

import org.coupon.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"org.coupon.couponservice", "org.coupon.sagapersistence"})
@EntityScan(basePackages = {"org.coupon.couponservice", "org.coupon.sagapersistence"})
@EnableJpaRepositories(basePackages = {"org.coupon.couponservice", "org.coupon.sagapersistence"})
@Import(GlobalExceptionHandler.class)
public class CouponServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CouponServiceApplication.class, args);
	}

}
