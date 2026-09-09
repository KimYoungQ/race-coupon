package org.coupon.orderservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseErrorDecoderTest {

    private final ApiResponseErrorDecoder decoder = new ApiResponseErrorDecoder(new ObjectMapper());

    @Test
    @DisplayName("원격 서비스가 준 에러 코드를 그대로 BusinessException 으로 바꾼다")
    void decodeKnownErrorCode() {
        // given
        Response response = errorResponse(409,
                "{\"success\":false,\"data\":null,\"errorCode\":\"COUPON_ALREADY_USED\",\"errorMessage\":\"이미 사용된 쿠폰입니다\"}");

        // when
        Exception decoded = decoder.decode("CouponClient#checkUsable(Long)", response);

        // then
        assertThat(decoded).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) decoded).getErrorCode()).isEqualTo(ErrorCode.COUPON_ALREADY_USED);
        assertThat(decoded.getMessage()).isEqualTo("이미 사용된 쿠폰입니다");
    }

    @Test
    @DisplayName("본문이 JSON 이 아니면 INTERNAL_ERROR 로 바꾼다")
    void decodeNonJsonBody() {
        // given
        Response response = errorResponse(503, "Service Unavailable");

        // when
        Exception decoded = decoder.decode("CouponClient#checkUsable(Long)", response);

        // then
        assertThat(decoded).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) decoded).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    private Response errorResponse(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/coupons/1/usable",
                Map.of(), Request.Body.empty(), null);
        return Response.builder()
                .status(status)
                .request(request)
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}
