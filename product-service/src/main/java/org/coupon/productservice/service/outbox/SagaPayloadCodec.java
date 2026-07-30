package org.coupon.productservice.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Outbox payload의 JSON 변환. order-service의 같은 이름 클래스와 설정이 동일해야 한다 —
 * 한쪽이 쓴 payload를 다른 쪽이 읽는 일은 없지만, 두 서비스가 같은 DTO 계약을 다루므로
 * 직렬화 전제가 갈리면 진단할 때 서로 다른 모양의 JSON을 보게 된다.
 *
 * <p><b>컨텍스트의 {@code ObjectMapper} 빈을 주입받지 않는다.</b> Boot 4.1에는 Jackson이 두 벌 있어
 * (starter-webmvc의 Jackson 3, eureka-client가 끌고 오는 Jackson 2) 어느 쪽이 주입될지가
 * 클래스패스 구성에 좌우된다. Outbox payload는 배포 간격을 넘어 DB에 남는 값이라
 * 형식이 우연에 흔들리면 안 된다.
 *
 * <p>Jackson 2를 쓰는 이유는 spring-kafka의 {@code JsonSerializer}가 Jackson 2 기반이기 때문이다.
 * 저장한 payload를 그대로 record로 되돌려 KafkaTemplate에 넘기므로 두 매퍼의 전제가 같아야 한다.
 */
@Component
public class SagaPayloadCodec {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            // Outbox row는 배포 간격을 넘어 살아남는다. 구버전 payload를 신버전 코드가 읽을 때
            // 필드가 늘었다고 깨지면 되돌릴 방법이 없다.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // 기본값인 숫자 타임스탬프로 두면 사가 진행 상황을 SQL로 좇을 때 payload를 읽을 수 없다.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 직렬화 실패는 계약 위반이다. 삼키면 내용 없는 Outbox row가 남아 사가가 조용히 멈춘다.
            throw new IllegalStateException(
                    "Saga payload 직렬화 실패: " + payload.getClass().getSimpleName(), e);
        }
    }

    public <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Saga payload 역직렬화 실패: " + type.getSimpleName(), e);
        }
    }
}
