package org.coupon.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 주문 생성 요청.
 *
 * <p>주문자(userId)는 <b>받지 않는다</b>. 검증된 토큰의 {@code sub}에서만 얻는다 —
 * 값을 적어 보내는 쪽이 주체를 정하게 되면 타인 명의 주문이 가능해진다.
 */
public record OrderCreateRequest(

        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        Integer quantity,

        /**
         * 사용할 쿠폰 ID(선택). 생략하면 할인 없이 주문한다.
         *
         * <p>발급 API가 돌려주는 {@code couponId}를 그대로 넣는다. 어느 발급 건을 쓸지는
         * coupon-service가 주문자와 이 값으로 찾으므로 클라이언트가 내부 식별자를 알 필요가 없다.
         */
        Long couponId
) {
}
