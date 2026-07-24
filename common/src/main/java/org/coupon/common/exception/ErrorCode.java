package org.coupon.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INTERNAL_ERROR("INTERNAL_ERROR", "서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT("INVALID_INPUT", "입력값이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "접근 권한이 없습니다", HttpStatus.FORBIDDEN),

    // 쿠폰
    COUPON_NOT_FOUND("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    COUPON_SOLD_OUT("COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다", HttpStatus.CONFLICT),
    INVALID_STRATEGY("INVALID_STRATEGY", "지원하지 않는 발급 전략입니다", HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT("INVALID_DISCOUNT", "할인 정보가 올바르지 않습니다", HttpStatus.BAD_REQUEST),

    // 인증 (user-service)
    DUPLICATE_USERNAME("DUPLICATE_USERNAME", "이미 사용 중인 아이디입니다", HttpStatus.CONFLICT),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "토큰이 만료되었습니다", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("INVALID_TOKEN", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
