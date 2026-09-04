package org.coupon.couponservice;

import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.support.DatabaseCleaner;
import org.coupon.couponservice.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainer.class, DatabaseCleaner.class})
class DataSeedIdempotencyTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private SqlInitializationProperties sqlInitializationProperties;

    @Test
    @DisplayName("테스트 설정의 data-locations 는 빈 리스트다 — data.sql 이 테스트 DB로 새지 않는 근거")
    void data_locations_are_empty_in_tests() {
        assertThat(sqlInitializationProperties.getMode().name()).isEqualTo("ALWAYS");
        assertThat(sqlInitializationProperties.getDataLocations()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("data.sql을 두 번 실행해도 시드 행 수는 4로 불변이다")
    void seed_is_idempotent() {
        databaseCleaner.clear();

        runSeed();
        runSeed();

        assertThat(couponRepository.count()).isEqualTo(4L);
        assertThat(couponRepository.findById(1L).orElseThrow().getTitle()).isEqualTo("선착순 쿠폰");

        databaseCleaner.clear();
    }

    private void runSeed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("data.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }
}
