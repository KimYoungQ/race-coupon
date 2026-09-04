package org.coupon.productservice;

import org.coupon.productservice.support.MySqlTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MySqlTestContainer.class)
class ProductServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
