package org.coupon.orderservice;

import org.coupon.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"org.coupon.orderservice", "org.coupon.sagapersistence"})
@EntityScan(basePackages = {"org.coupon.orderservice", "org.coupon.sagapersistence"})
@EnableJpaRepositories(basePackages = {"org.coupon.orderservice", "org.coupon.sagapersistence"})
@EnableFeignClients
@Import(GlobalExceptionHandler.class)
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
