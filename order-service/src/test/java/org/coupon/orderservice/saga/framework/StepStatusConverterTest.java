package org.coupon.orderservice.saga.framework;

import org.coupon.common.saga.SagaStepStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepStatusConverterTest {

    private final StepStatusConverter converter = new StepStatusConverter();

    @Test
    @DisplayName("맵 → JSON → 맵 왕복이 순서와 값을 보존한다")
    void round_trips() {
        Map<String, SagaStepStatus> statuses = new LinkedHashMap<>();
        statuses.put("stock-reservation", SagaStepStatus.SUCCEEDED);
        statuses.put("coupon-apply", SagaStepStatus.FAILED);

        String json = converter.convertToDatabaseColumn(statuses);

        assertThat(json).isEqualTo("{\"stock-reservation\":\"SUCCEEDED\",\"coupon-apply\":\"FAILED\"}");
        assertThat(converter.convertToEntityAttribute(json)).containsExactlyEntriesOf(statuses);
    }

    @Test
    @DisplayName("null 과 빈 맵은 {} 로, {} 와 null 은 빈 맵으로")
    void empty_cases() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
        assertThat(converter.convertToDatabaseColumn(Map.of())).isEqualTo("{}");
        assertThat(converter.convertToEntityAttribute("{}")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }
}
