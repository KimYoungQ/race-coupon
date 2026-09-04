package org.coupon.userservice;

import org.coupon.userservice.support.MySqlTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MySqlTestContainer.class)
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
