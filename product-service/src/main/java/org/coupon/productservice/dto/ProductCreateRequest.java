package org.coupon.productservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 상품 등록 요청. 재고 초기값은 여기서만 정해진다 —
 * 등록 이후의 재고 변경 경로는 주문 Saga의 Kafka 커맨드뿐이다.
 */
public record ProductCreateRequest(

        @NotBlank(message = "상품명은 필수입니다")
        String name,

        @NotNull(message = "가격은 필수입니다")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다")
        Long price,

        @NotNull(message = "재고는 필수입니다")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다")
        Long stock
) {
}
