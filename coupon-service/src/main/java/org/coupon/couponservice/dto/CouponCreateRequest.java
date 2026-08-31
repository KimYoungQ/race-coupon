package org.coupon.couponservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.coupon.couponservice.domain.DiscountType;

import java.time.LocalDateTime;

public record CouponCreateRequest(

        @NotBlank(message = "쿠폰명은 필수입니다")
        String title,

        @NotNull(message = "총 수량은 필수입니다")
        @Min(value = 1, message = "총 수량은 1 이상이어야 합니다")
        Long totalQuantity,

        @NotNull(message = "할인 유형은 필수입니다")
        DiscountType discountType,

        @NotNull(message = "할인 값은 필수입니다")
        Long discountValue,

        Long maxDiscountAmount,

        Long minOrderAmount,

        @NotNull(message = "이벤트 종료 시각은 필수입니다")
        @Future(message = "이벤트 종료 시각은 미래여야 합니다")
        LocalDateTime eventEndAt
) {
}
