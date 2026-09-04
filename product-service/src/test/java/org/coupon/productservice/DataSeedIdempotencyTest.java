package org.coupon.productservice;

import org.coupon.productservice.repository.ProductRepository;
import org.coupon.productservice.support.MySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
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
@Import(MySqlTestContainer.class)
class DataSeedIdempotencyTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SqlInitializationProperties sqlInitializationProperties;

    @Test
    @DisplayName("테스트 설정의 data-locations 는 빈 리스트다 — data.sql 이 테스트 DB로 새지 않는 근거")
    void data_locations_are_empty_in_tests() {
        assertThat(sqlInitializationProperties.getMode().name()).isEqualTo("ALWAYS");
        assertThat(sqlInitializationProperties.getDataLocations()).isNotNull().isEmpty();
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("data.sql 을 두 번 실행해도 시드 행 수는 3으로 불변이다")
    void data_sql_is_idempotent() {
        assertThat(productRepository.count())
                .as("테스트에는 data.sql 이 적용되지 않아야 한다(data-locations 비움)")
                .isZero();

        runDataSql();
        runDataSql();

        assertThat(productRepository.count()).isEqualTo(3);
    }

    private void runDataSql() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("data.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }
}
