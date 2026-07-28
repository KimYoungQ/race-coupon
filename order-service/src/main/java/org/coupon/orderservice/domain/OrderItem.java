package org.coupon.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 품목. 1주문 1상품이 확정이므로 한 주문에 한 행만 존재한다
 * (테이블은 다품목 확장 시 마이그레이션을 피하려고 유지한다).
 *
 * <p>{@code productName}·{@code unitPrice}는 <b>주문 시점 스냅샷</b>이다.
 * 상품 가격이 나중에 바뀌어도 지나간 주문 이력이 따라 흔들리면 안 된다.
 * 가격의 권위는 product-service에 있어 주문 생성 시점에는 알 수 없고,
 * 재고 예약 리플라이가 도착해야 채워진다 — 그래서 nullable이다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** product-service의 식별자. 서비스 경계를 넘으므로 값 참조만 가능하다. */
    @Column(nullable = false)
    private Long productId;

    private String productName;

    private Long unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Builder
    private OrderItem(Order order, Long productId, Integer quantity) {
        this.order = order;
        this.productId = productId;
        this.quantity = quantity;
    }

    /**
     * 재고 예약 리플라이가 실어온 상품명·단가를 주문 이력에 고정한다.
     * 호출 시점은 {@link Order#reserveStock(String, long)} 한 곳뿐이라 상태 검증은 그쪽이 담당한다.
     */
    void applyPriceSnapshot(String productName, long unitPrice) {
        this.productName = productName;
        this.unitPrice = unitPrice;
    }
}
