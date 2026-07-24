package org.coupon.couponservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.coupon.couponservice.domain.DiscountType;

/**
 * 관리자 쿠폰 등록 요청.
 *
 * <p>할인 값의 유형별 허용 범위(정률 1~100 등)는 여기서 검증하지 않는다.
 * {@link DiscountType#validate(long)}가 도메인 불변식으로 이미 지키고 있어,
 * 같은 규칙을 DTO에도 적으면 두 곳이 어긋날 수 있다.
 */
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

        /** 최대 할인 한도(선택). null이면 한도 없음. */
        Long maxDiscountAmount,

        /** 최소 주문 금액(선택). null이면 조건 없음. */
        Long minOrderAmount
) {
}
