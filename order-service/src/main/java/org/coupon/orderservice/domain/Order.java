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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문이자 Saga 인스턴스. 재고 예약 → 쿠폰 사용으로 이어지는 사가의 진행 상태를 이 row가 보관한다.
 *
 * <p><b>상태 전이는 이 도메인이 스스로 검증한다.</b> 허용되지 않은 상태에서의 전이는
 * {@link InvalidOrderStateException}으로 거부된다. 이 가드는 불변식 보호인 동시에
 * Kafka 리플라이가 중복 수신됐을 때의 멱등성 방어를 겸한다 — 이미 COMPLETED가 된 주문에
 * 같은 리플라이가 한 번 더 도착해도 금액이 두 번 반영되지 않는다.
 *
 * <p>{@code user_id}·{@code issued_coupon_id}는 다른 서비스의 스키마를 가리키므로
 * FK도 연관관계도 만들 수 없고 {@code Long} 값 참조만 가능하다.
 */
@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** 사용할 발급 쿠폰. null이면 쿠폰 없는 주문이라 사가가 재고 예약 후 바로 완료된다. */
    private Long issuedCouponId;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private Long finalAmount;

    /** 실패 원인. 성공한 주문에서는 null이다. */
    private String failureCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * 주문 생성 시점에 확정되는 것은 "누가 어떤 상품을 몇 개" 뿐이다.
     * 금액은 재고 예약 리플라이가 단가를 실어올 때까지 알 수 없어 0으로 시작한다.
     *
     * <p>품목을 빌더 인자가 아니라 여기서 직접 만든다 — 1주문 1상품 불변식을
     * 생성 시점에 고정해야 이후 어느 경로로도 품목 0개·2개인 주문이 생기지 않는다.
     */
    @Builder
    private Order(Long userId, Long issuedCouponId, Long productId, Integer quantity) {
        this.userId = userId;
        this.issuedCouponId = issuedCouponId;
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

    /**
     * 재고 예약 성공. 리플라이가 실어온 상품명·단가를 품목에 고정하고 주문 총액을 확정한다.
     *
     * <p>총액을 인자로 받지 않고 {@code unitPrice * quantity}로 직접 계산한다.
     * 수량을 아는 쪽이 이 객체이므로 계산을 밖에 두면 곱셈이 어긋나도 도메인이 알 수 없다.
     */
    public void reserveStock(String productName, long unitPrice) {
        requireStatus(OrderStatus.CREATED);
        OrderItem item = primaryItem();
        item.applyPriceSnapshot(productName, unitPrice);
        this.totalAmount = unitPrice * item.getQuantity();
        this.finalAmount = this.totalAmount;
        this.status = OrderStatus.STOCK_RESERVED;
        touch();
    }

    /**
     * 사가 성공. 쿠폰 없는 주문은 재고 예약 직후 CREATED에서도 곧바로 완료될 수 있어
     * 두 상태를 모두 허용한다.
     */
    public void complete(long discountAmount, long finalAmount) {
        requireStatus(OrderStatus.CREATED, OrderStatus.STOCK_RESERVED);
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.status = OrderStatus.COMPLETED;
        touch();
    }

    /** 보상 시작. 이미 차감된 재고를 되돌리는 동안의 상태다. */
    public void startCompensation(String failureCode) {
        requireStatus(OrderStatus.CREATED, OrderStatus.STOCK_RESERVED);
        this.failureCode = failureCode;
        this.status = OrderStatus.COMPENSATING;
        touch();
    }

    /**
     * 최종 실패. 재고 예약 자체가 실패해 되돌릴 것이 없는 경우(CREATED)와
     * 보상을 마친 경우(COMPENSATING) 양쪽에서 도달한다.
     */
    public void fail(String failureCode) {
        requireStatus(OrderStatus.CREATED, OrderStatus.COMPENSATING);
        this.failureCode = failureCode;
        this.status = OrderStatus.FAILED;
        touch();
    }

    /** 1주문 1상품이 확정이므로 품목은 항상 정확히 하나다(생성자가 보장). */
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
