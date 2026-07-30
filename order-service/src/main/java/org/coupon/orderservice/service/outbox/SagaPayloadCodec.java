package org.coupon.orderservice.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Outbox payload의 JSON 변환.
 *
 * <p><b>컨텍스트의 {@code ObjectMapper} 빈을 주입받지 않는다.</b> Boot 4.1에는 Jackson이 두 벌 있고
 * (starter-webmvc의 Jackson 3, eureka-client가 끌고 오는 Jackson 2) 어느 쪽이 주입될지가
 * 클래스패스 구성에 따라 달라진다. 사가 payload는 배포 간격을 넘어 DB에 남는 값이라
 * 그 형식이 우연에 좌우되면 안 된다. 그래서 필요한 설정을 직접 명시한다.
 *
 * <p>Jackson 2를 쓰는 이유는 spring-kafka의 {@code JsonSerializer}가 Jackson 2 기반이기 때문이다.
 * DB에 저장한 payload를 그대로 record로 되돌려 KafkaTemplate에 넘기므로 두 매퍼의 전제가 같아야 한다.
 *
 * <p>네 가지 설정에 각각 이유가 있다.
 * <ul>
 *   <li>{@code JavaTimeModule} — DTO의 {@code Instant}를 다루려면 필수다</li>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES} off — Outbox row는 배포 간격을 넘어 살아남는다.
 *       구버전 payload를 신버전 코드가 읽을 때 필드가 늘었다고 깨지면 되돌릴 방법이 없다</li>
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS} off — 기본값인 숫자 타임스탬프로 두면
 *       사가 진행 상황을 SQL로 좇을 때 payload를 읽을 수가 없다</li>
 *   <li>{@code DEFAULT_VIEW_INCLUSION} off — JSON view 설정이 새어 들어와 필드가 누락되는 것을 막는다</li>
 * </ul>
 */
@Component
public class SagaPayloadCodec {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
