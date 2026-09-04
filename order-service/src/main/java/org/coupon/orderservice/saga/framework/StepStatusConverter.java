package org.coupon.orderservice.saga.framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.coupon.common.saga.SagaStepStatus;

import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class StepStatusConverter implements AttributeConverter<Map<String, SagaStepStatus>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, SagaStepStatus>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, SagaStepStatus> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("step_status 직렬화 실패", e);
        }
    }

    @Override
    public Map<String, SagaStepStatus> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("step_status 역직렬화 실패: " + dbData, e);
        }
    }
}
