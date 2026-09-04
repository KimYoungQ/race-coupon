package org.coupon.couponservice;

import org.coupon.couponservice.support.MySqlTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MySqlTestContainer.class)
class CouponServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
