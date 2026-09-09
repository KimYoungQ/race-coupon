package org.coupon.orderservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.common.response.ApiResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * 다른 서비스가 돌려준 ApiResponse 의 errorCode 를 같은 ErrorCode 의 BusinessException 으로 바꾼다.
 * 모든 서비스가 같은 ErrorCode 규약을 쓰므로 호출한 쪽에서도 같은 HTTP 상태로 응답할 수 있다.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiResponseErrorDecoder implements ErrorDecoder {

    private static final TypeReference<ApiResponse<Void>> ERROR_BODY = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        ApiResponse<Void> body = readBody(methodKey, response);
        if (body == null || body.getErrorCode() == null) {
            log.warn("원격 서비스가 에러 코드 없이 실패 응답: method={}, status={}", methodKey, response.status());
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "원격 서비스 호출에 실패했습니다: " + methodKey);
        }
        try {
            return new BusinessException(ErrorCode.valueOf(body.getErrorCode()), body.getErrorMessage());
        } catch (IllegalArgumentException e) {
            log.warn("원격 서비스가 모르는 에러 코드 응답: method={}, errorCode={}", methodKey, body.getErrorCode());
            return new BusinessException(ErrorCode.INTERNAL_ERROR, body.getErrorMessage());
        }
    }

    private ApiResponse<Void> readBody(String methodKey, Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            return objectMapper.readValue(in, ERROR_BODY);
        } catch (IOException e) {
            log.warn("원격 서비스 에러 응답 본문을 읽지 못함: method={}, status={}", methodKey, response.status(), e);
            return null;
        }
    }
}
