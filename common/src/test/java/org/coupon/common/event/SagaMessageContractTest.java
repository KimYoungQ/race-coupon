package org.coupon.common.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saga 메시지 계약 테스트.
 *
 * <p>Avro Schema Registry를 쓰지 않으므로 <b>DTO의 필드 이름·enum 값·JSON 형식이 곧 계약</b>이다.
 * producer와 consumer가 서로 다른 배포 시점에 있을 수 있고, Outbox payload는 배포 간격을 넘어
 * 살아남는다. 필드명이 바뀌면 예외 없이 조용히 null로 역직렬화되므로, 그 변경이 리뷰 없이
 * 통과하지 못하도록 여기서 고정한다.
 *
 * <p>역직렬화 테스트는 {@code -parameters} 컴파일 옵션의 회귀 테스트를 겸한다.
 * 옵션이 빠지면 record 컴포넌트가 {@code arg0, arg1}로 바인딩돼 이 테스트가 깨진다.
 */
class SagaMessageContractTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SAGA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATED_AT = Instant.parse("2026-07-29T05:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    @DisplayName("토픽은 정확히 4개이며 이름이 고정이다")
    void topics_are_fixed() {
        assertThat(SagaTopics.PRODUCT_REQUEST).isEqualTo("product-request");
        assertThat(SagaTopics.PRODUCT_RESPONSE).isEqualTo("product-response");
        assertThat(SagaTopics.COUPON_REQUEST).isEqualTo("coupon-request");
        assertThat(SagaTopics.COUPON_RESPONSE).isEqualTo("coupon-response");
    }

    @Test
    @DisplayName("enum 값 이름은 계약이므로 변경·제거되지 않는다")
    void enum_names_are_fixed() {
        assertThat(StockOrderStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "CANCELLED");
        assertThat(StockStatus.values()).extracting(Enum::name)
                .containsExactly("RESERVED", "RESTORED", "FAILED");
        assertThat(CouponOrderStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "CANCELLED");
        assertThat(CouponStatus.values()).extracting(Enum::name)
                .containsExactly("APPLIED", "RESTORED", "FAILED");
    }

    @Nested
    @DisplayName("StockRequest")
    class ProductRequestContract {

        private final StockRequest sample = new StockRequest(
                ID, SAGA_ID, 100L, 7L, 2, StockOrderStatus.PENDING, CREATED_AT);

        @Test
        @DisplayName("JSON 필드명과 형식이 고정이다")
        void serializes_to_fixed_json() throws Exception {
            assertThat(mapper.writeValueAsString(sample)).isEqualTo("""
                    {"id":"11111111-1111-1111-1111-111111111111",\
                    "sagaId":"22222222-2222-2222-2222-222222222222",\
                    "orderId":100,\
                    "productId":7,\
                    "quantity":2,\
                    "stockOrderStatus":"PENDING",\
                    "createdAt":"2026-07-29T05:00:00Z"}""");
        }

        @Test
        @DisplayName("왕복 후 값이 보존된다 (-parameters 회귀 테스트)")
        void round_trips() throws Exception {
            String json = mapper.writeValueAsString(sample);
            assertThat(mapper.readValue(json, StockRequest.class)).isEqualTo(sample);
        }

        @Test
        @DisplayName("모르는 필드는 무시한다 — 구버전 consumer가 신버전 payload를 읽을 수 있어야 한다")
        void ignores_unknown_fields() throws Exception {
            String json = mapper.writeValueAsString(sample).replaceFirst("\\{", "{\"newField\":1,");
            assertThat(mapper.readValue(json, StockRequest.class)).isEqualTo(sample);
        }
    }

    @Nested
    @DisplayName("StockResponse")
    class ProductResponseContract {

        private final StockResponse sample = new StockResponse(
                ID, SAGA_ID, 100L, 7L, StockStatus.RESERVED, "노트북", 1_500_000L, List.of(), CREATED_AT);

        @Test
        @DisplayName("JSON 필드명과 형식이 고정이다")
        void serializes_to_fixed_json() throws Exception {
            assertThat(mapper.writeValueAsString(sample)).isEqualTo("""
                    {"id":"11111111-1111-1111-1111-111111111111",\
                    "sagaId":"22222222-2222-2222-2222-222222222222",\
                    "orderId":100,\
                    "productId":7,\
                    "stockStatus":"RESERVED",\
                    "productName":"노트북",\
                    "unitPrice":1500000,\
                    "failureMessages":[],\
                    "createdAt":"2026-07-29T05:00:00Z"}""");
        }

        @Test
        @DisplayName("왕복 후 값이 보존된다")
        void round_trips() throws Exception {
            String json = mapper.writeValueAsString(sample);
            assertThat(mapper.readValue(json, StockResponse.class)).isEqualTo(sample);
        }

        @Test
        @DisplayName("failureMessages는 null이 들어와도 빈 리스트로 정규화된다")
        void normalizes_null_failure_messages() throws Exception {
            StockResponse withNull = new StockResponse(
                    ID, SAGA_ID, 100L, 7L, StockStatus.FAILED, null, null, null, CREATED_AT);
            assertThat(withNull.failureMessages()).isEmpty();

            StockResponse parsed = mapper.readValue(
                    mapper.writeValueAsString(withNull), StockResponse.class);
            assertThat(parsed.failureMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CouponRequest")
    class CouponRequestContract {

        private final CouponRequest sample = new CouponRequest(
                ID, SAGA_ID, 100L, 42L, 9L, 3_000_000L, CouponOrderStatus.PENDING, CREATED_AT);

        @Test
        @DisplayName("issuedCouponId가 아니라 userId + couponId를 싣는다")
        void carries_user_and_coupon_id() throws Exception {
            String json = mapper.writeValueAsString(sample);
            assertThat(json).contains("\"userId\":42", "\"couponId\":9");
            assertThat(json).doesNotContain("issuedCouponId");
        }

        @Test
        @DisplayName("JSON 필드명과 형식이 고정이다")
        void serializes_to_fixed_json() throws Exception {
            assertThat(mapper.writeValueAsString(sample)).isEqualTo("""
                    {"id":"11111111-1111-1111-1111-111111111111",\
                    "sagaId":"22222222-2222-2222-2222-222222222222",\
                    "orderId":100,\
                    "userId":42,\
                    "couponId":9,\
                    "orderAmount":3000000,\
                    "couponOrderStatus":"PENDING",\
                    "createdAt":"2026-07-29T05:00:00Z"}""");
        }

        @Test
        @DisplayName("왕복 후 값이 보존된다")
        void round_trips() throws Exception {
            String json = mapper.writeValueAsString(sample);
            assertThat(mapper.readValue(json, CouponRequest.class)).isEqualTo(sample);
        }
    }

    @Nested
    @DisplayName("CouponResponse")
    class CouponResponseContract {

        private final CouponResponse sample = new CouponResponse(
                ID, SAGA_ID, 100L, 9L, 555L, CouponStatus.APPLIED,
                300_000L, 2_700_000L, List.of(), CREATED_AT);

        @Test
        @DisplayName("JSON 필드명과 형식이 고정이다")
        void serializes_to_fixed_json() throws Exception {
            assertThat(mapper.writeValueAsString(sample)).isEqualTo("""
                    {"id":"11111111-1111-1111-1111-111111111111",\
                    "sagaId":"22222222-2222-2222-2222-222222222222",\
                    "orderId":100,\
                    "couponId":9,\
                    "issuedCouponId":555,\
                    "couponStatus":"APPLIED",\
                    "discountAmount":300000,\
                    "finalAmount":2700000,\
                    "failureMessages":[],\
                    "createdAt":"2026-07-29T05:00:00Z"}""");
        }

        @Test
        @DisplayName("왕복 후 값이 보존된다")
        void round_trips() throws Exception {
            String json = mapper.writeValueAsString(sample);
            assertThat(mapper.readValue(json, CouponResponse.class)).isEqualTo(sample);
        }

        @Test
        @DisplayName("해석 실패 시 issuedCouponId는 null이다")
        void issued_coupon_id_is_null_when_unresolved() throws Exception {
            CouponResponse notIssued = new CouponResponse(
                    ID, SAGA_ID, 100L, 9L, null, CouponStatus.FAILED,
                    null, null, List.of("COUPON_NOT_ISSUED_YET"), CREATED_AT);

            CouponResponse parsed = mapper.readValue(
                    mapper.writeValueAsString(notIssued), CouponResponse.class);
            assertThat(parsed.issuedCouponId()).isNull();
            assertThat(parsed.failureMessages()).containsExactly("COUPON_NOT_ISSUED_YET");
        }
    }
}
