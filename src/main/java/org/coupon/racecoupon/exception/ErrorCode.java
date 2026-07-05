package org.coupon.racecoupon.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_ERROR("INTERNAL_ERROR", "서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    COUPON_NOT_FOUND("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    COUPON_SOLD_OUT("COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
