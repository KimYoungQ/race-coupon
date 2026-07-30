package org.coupon.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 쿠폰 적용·복구 요청. order-service → coupon-service ({@code coupon-request}).
 *
 * <p><b>{@code issuedCouponId}(내부 PK)가 아니라 {@code couponId}를 싣는다.</b>
 * 발급 API가 {@code issuedCouponId}를 어디서도 돌려주지 않아 클라이언트가 그 값을 알 수 없고,
 * 발급 row 자체가 Kafka 컨슈머에 의해 비동기로 나중에 생성되기 때문이다.
 * 어느 발급 row를 쓸지는 coupon-service가 {@code (userId, couponId, ISSUED)}로 해석한다 —
 * {@code IssuedCoupon}의 {@code UNIQUE(user_id, coupon_id)}가 그 해석을 1건으로 확정한다.
 *
 * <p>{@link #userId}는 <b>반드시 서버 측 값</b>이어야 한다. 주문 생성 시 인증된 principal에서 얻는다.
 * 클라이언트가 채우게 하면 타인 명의로 쿠폰을 쓸 수 있다.
 *
 * @param id                개별 메시지 식별자
 * @param sagaId            전체 주문 Saga 상관 키
 * @param orderId           주문 및 오케스트레이터 상태 조회 식별자
 * @param userId            쿠폰 소유자. 인증 principal에서 온 값이며 파티션 키의 앞부분
 * @param couponId          쿠폰 템플릿 식별자. 파티션 키의 뒷부분
 * @param orderAmount       할인 계산 입력. coupon-service가 order 테이블을 조회하지 않도록 실어 보낸다
 * @param couponOrderStatus PENDING(적용) / CANCELLED(복구)
 * @param createdAt         메시지 생성 시각. <b>진단·추적 전용</b>
 */
public record CouponRequest(
        UUID id,
        UUID sagaId,
        Long orderId,
        Long userId,
        Long couponId,
        Long orderAmount,
        CouponOrderStatus couponOrderStatus,
        Instant createdAt
) {
}
