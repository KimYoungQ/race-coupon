package org.coupon.common.event;

import java.time.Instant;
import java.util.UUID;

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
