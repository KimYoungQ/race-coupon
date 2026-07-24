package org.coupon.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 전 서비스 공통 예외 처리. coupon-api·user-service 등 이 모듈을 쓰는 모든 서비스가 공유한다.
 * 새 예외가 생겨도 이 핸들러는 수정하지 않는다 — ErrorCode 상수 추가 + BusinessException 상속이면 끝이다.
 *
 * <p>패키지가 다른 서비스(예: org.coupon.userservice)는 컴포넌트 스캔에 잡히지 않으므로
 * 애플리케이션 클래스에 {@code @Import(GlobalExceptionHandler.class)}를 붙여야 한다.
 *
 * <p>서블릿 웹 앱에서만 등록한다. coupon-consumer처럼 같은 베이스 패키지를 쓰는 비웹 모듈이
 * 이 클래스를 스캔하면 servlet 타입을 못 찾아 기동이 실패한다(HTTP 응답을 만들 일도 없다).
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("비즈니스 예외: [{}] {}", errorCode.getCode(), e.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    /**
     * {@code @Valid @RequestBody} 검증 실패. 필드별 한글 message를 모아 한 번에 내려준다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("입력값 검증 실패: {}", message);
        return badRequest(message);
    }

    /**
     * {@code @PathVariable}/{@code @RequestParam} 검증 실패(컨트롤러의 {@code @Validated}와 짝).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("파라미터 검증 실패: {}", message);
        return badRequest(message);
    }

    /**
     * 필수 요청 값 누락(헤더·파라미터·쿠키·경로변수).
     * 이 핸들러가 없으면 아래 handleGeneral로 떨어져 클라이언트 잘못인데도 500이 나간다.
     */
    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestValue(MissingRequestValueException e) {
        log.warn("필수 요청 값 누락: {}", e.getMessage());
        return badRequest(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        log.error("예상치 못한 오류: {}", e.getMessage(), e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message));
    }
}
