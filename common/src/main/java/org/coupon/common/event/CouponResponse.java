package org.coupon.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public CouponResponse {
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
    }
}
