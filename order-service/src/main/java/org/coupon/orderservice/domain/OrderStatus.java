package org.coupon.orderservice.domain;

public enum OrderStatus {

    CREATED,
    STOCK_RESERVED,
    COMPLETED,
    COMPENSATING,
    FAILED
}
