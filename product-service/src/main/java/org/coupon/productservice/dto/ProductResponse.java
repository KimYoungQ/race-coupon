package org.coupon.productservice.dto;

import java.time.LocalDateTime;

public record ProductResponse(
        Long productId,
        String name,
        Long price,
        Long stock,
        LocalDateTime createdAt
) {
}
