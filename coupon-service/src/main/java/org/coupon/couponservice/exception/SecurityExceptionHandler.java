package org.coupon.couponservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.coupon.common.exception.ErrorCode;
import org.coupon.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @PreAuthorize} 인가 거부를 403으로 변환한다.
 *
 * <p>{@link GlobalExceptionHandler}보다 먼저 평가돼야 한다. 그쪽의
 * {@code @ExceptionHandler(Exception.class)}가 먼저 잡으면 403이어야 할 응답이 500으로 나간다.
 * 어드바이스 간 우선순위는 클래스 단위 {@code @Order}로만 정해진다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        log.warn("인가 거부: {}", e.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}
