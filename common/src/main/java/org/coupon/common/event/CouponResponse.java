package org.coupon.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 쿠폰 처리 결과. coupon-service → order-service ({@code coupon-response}).
 *
 * <p>{@link #issuedCouponId}는 <b>응답에만</b> 등장한다. 요청은 {@code couponId}를 싣지만
 * 참여자가 실제로 어느 발급 row를 건드렸는지는 진단에 필요하기 때문이다.
 * 오케스트레이터는 이 값을 상관 키로 쓰지 않는다 — 단계 상관 키는
 * {@code sagaId + requestStatus + 기대 SagaStatus}다.
 *
 * @param id              개별 메시지 식별자
 * @param sagaId          전체 주문 Saga 상관 키
 * @param orderId         주문 식별자이자 파티션 키
 * @param couponId        요청 echo
 * @param issuedCouponId  coupon-service가 해석한 발급 row. 해석 실패 시 null
 * @param couponStatus    APPLIED / RESTORED / FAILED. listener는 이 값으로 분기한다
 * @param discountAmount  APPLIED일 때만 채워지는 실효 할인액(0 하한 적용 후)
 * @param finalAmount     APPLIED일 때만 채워지는 최종 결제가
 * @param failureMessages 실패 원인. null이 아니라 빈 리스트를 쓴다
 * @param createdAt       메시지 생성 시각. <b>진단·추적 전용</b>
 */
public record CouponResponse(
        UUID id,
        UUID sagaId,
        Long orderId,
        Long couponId,
        Long issuedCouponId,
        CouponStatus couponStatus,
        Long discountAmount,
        Long finalAmount,
        List<String> failureMessages,
        Instant createdAt
) {

    /** {@code failureMessages}를 항상 non-null 불변 리스트로 정규화한다. */
    public CouponResponse {
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
    }
}
