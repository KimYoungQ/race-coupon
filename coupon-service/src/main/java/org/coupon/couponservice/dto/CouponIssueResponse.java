package org.coupon.couponservice.dto;

/**
 * 발급 응답.
 *
 * <p><b>201 CREATED가 곧 {@code IssuedCoupon} row의 존재를 뜻하지는 않는다.</b>
 * 발급은 Redis 카운터로 수량을 확보한 뒤 Kafka로 생성 메시지를 보내고 즉시 응답하며,
 * row는 컨슈머가 나중에 만든다. 201의 의미는 정확히 이만큼이다.
 *
 * <pre>
 *   201 CREATED = Redis 발급 수량을 확보했고, 비동기 IssuedCoupon 생성 메시지를 발행 요청했다.
 *              ≠ IssuedCoupon row가 생성되었다.
 * </pre>
 *
 * <p>그래서 발급 직후 주문하면 주문 사가가 발급 건을 못 찾아
 * {@code COUPON_NOT_ISSUED_YET}으로 실패할 수 있다. 조금 뒤 재주문하면 성공한다.
 *
 * <p>발급 건의 내부 PK를 돌려주지 않는 것은 의도적이다. 주문할 때는 여기 담긴 {@code couponId}를
 * 그대로 넘기면 되고, 어느 발급 건을 쓸지는 coupon-service가 주문자와 함께 해석한다.
 *
 * @param couponId       발급받은 쿠폰. 주문 시 이 값을 그대로 넘긴다
 * @param issuedQuantity 이 요청 시점의 누적 발급 수량(Redis 카운터 기준)
 * @param remaining      남은 수량
 */
public record CouponIssueResponse(Long couponId, Long issuedQuantity, Long remaining) {
}
