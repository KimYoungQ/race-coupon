package org.coupon.orderservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private UUID sagaId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private Long couponId;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private Long finalAmount;

    private String failureCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Builder
    private Order(Long userId, Long couponId, Long productId, Integer quantity) {
        this.sagaId = UUID.randomUUID();
        this.userId = userId;
        this.couponId = couponId;
        this.status = OrderStatus.CREATED;
        this.totalAmount = 0L;
        this.discountAmount = 0L;
        this.finalAmount = 0L;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.items.add(OrderItem.builder()
                .order(this)
                .productId(productId)
                .quantity(quantity)
                .build());
    }

    public void reserveStock(String productName, long unitPrice) {
        requireStatus(OrderStatus.CREATED);
        OrderItem item = primaryItem();
        item.applyPriceSnapshot(productName, unitPrice);
        this.totalAmount = unitPrice * item.getQuantity();
        this.finalAmount = this.totalAmount;
        this.status = OrderStatus.STOCK_RESERVED;
        touch();
    }

    public void complete(long discountAmount, long finalAmount) {
        requireStatus(OrderStatus.STOCK_RESERVED);
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.status = OrderStatus.COMPLETED;
        touch();
    }

    public void startCompensation(String failureCode) {
        requireStatus(OrderStatus.CREATED, OrderStatus.STOCK_RESERVED);
        this.failureCode = failureCode;
        this.status = OrderStatus.COMPENSATING;
        touch();
    }

    public void fail(String failureCode) {
        requireStatus(OrderStatus.CREATED, OrderStatus.COMPENSATING);
        this.failureCode = failureCode;
        this.status = OrderStatus.FAILED;
        touch();
    }

    public void completeCompensation() {
        requireStatus(OrderStatus.COMPENSATING);
        this.status = OrderStatus.FAILED;
        touch();
    }

    public OrderItem primaryItem() {
        return items.get(0);
    }

    private void requireStatus(OrderStatus... allowed) {
        for (OrderStatus candidate : allowed) {
            if (this.status == candidate) {
                return;
            }
        }
        throw new InvalidOrderStateException(this.status);
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
