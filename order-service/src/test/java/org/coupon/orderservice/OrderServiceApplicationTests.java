package org.coupon.orderservice;

import org.coupon.orderservice.support.MySqlTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MySqlTestContainer.class)
class OrderServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
