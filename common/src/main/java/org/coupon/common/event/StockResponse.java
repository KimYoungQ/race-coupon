package org.coupon.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 재고 처리 결과. product-service → order-service ({@code product-response}).
 *
 * <p>{@link #productName}·{@link #unitPrice}는 필수 상관 필드가 아니지만 반드시 싣는다.
 * {@code Order.reserveStock(String, long)}이 <b>응답이 단가를 실어올 것을 전제로</b> 설계돼 있어,
 * 빼면 order-service가 product 테이블을 직접 조회해야 하고 서비스 경계가 무너진다.
 * RESERVED가 아닐 때는 null이다.
 *
 * @param id              개별 메시지 식별자
 * @param sagaId          전체 주문 Saga 상관 키
 * @param orderId         주문 식별자이자 파티션 키
 * @param productId       참여자 도메인 식별자
 * @param stockStatus     RESERVED / RESTORED / FAILED. listener는 이 값으로 분기한다
 * @param productName     RESERVED일 때만 채워지는 주문 시점 상품명 스냅샷
 * @param unitPrice       RESERVED일 때만 채워지는 주문 시점 단가 스냅샷
 * @param failureMessages 실패 원인. null이 아니라 빈 리스트를 쓴다
 * @param createdAt       메시지 생성 시각. <b>진단·추적 전용</b>
 */
public record StockResponse(
        UUID id,
        UUID sagaId,
        Long orderId,
        Long productId,
        StockStatus stockStatus,
        String productName,
        Long unitPrice,
        List<String> failureMessages,
        Instant createdAt
) {

    /** {@code failureMessages}를 항상 non-null 불변 리스트로 정규화한다. */
    public StockResponse {
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
    }
}
