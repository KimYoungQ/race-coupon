package org.coupon.common.event;

import java.time.Instant;
import java.util.UUID;

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
