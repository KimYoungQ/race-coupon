package org.coupon.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public StockResponse {
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
    }
}
