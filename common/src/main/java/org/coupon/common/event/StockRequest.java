package org.coupon.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 재고 예약·복구 요청. order-service → product-service ({@code product-request}).
 *
 * <p>정상 예약과 보상 복구가 <b>같은 토픽</b>을 쓰고 {@link #stockOrderStatus}로만 갈린다.
 * 그래야 같은 {@code productId}의 모든 재고 변경이 한 파티션에서 직렬화된다.
 *
 * @param id               개별 메시지 식별자. 같은 sagaId의 여러 메시지를 구분한다
 * @param sagaId           전체 주문 Saga 상관 키. Outbox 조회의 주 키
 * @param orderId          주문 및 오케스트레이터 상태 조회 식별자
 * @param productId        참여자 도메인 식별자이자 파티션 키
 * @param quantity         예약 또는 복구할 수량
 * @param stockOrderStatus PENDING(예약) / CANCELLED(복구)
 * @param createdAt        메시지 생성 시각. <b>진단·추적 전용</b>이며 어떤 비즈니스 판단에도 쓰지 않는다
 */
public record StockRequest(
        UUID id,
        UUID sagaId,
        Long orderId,
        Long productId,
        Integer quantity,
        StockOrderStatus stockOrderStatus,
        Instant createdAt
) {
}
