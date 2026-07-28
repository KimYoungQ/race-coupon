package org.coupon.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어떤 주문 때문에 어떤 상품의 재고를 얼마나 잡아뒀는지 기록한다.
 *
 * <p>{@code orderId}의 UNIQUE 제약은 FK가 아니라 <b>멱등 키</b>다. Kafka는 at-least-once라
 * 같은 재고 예약 커맨드를 두 번 받을 수 있는데, 그때 재고가 두 번 깎이는 것을 DB가 막는다.
 * 주문 서비스는 다른 스키마에 있어 FK를 걸 수 없고, {@code orderId}는 값 참조다.
 *
 * <p>반면 {@code product}는 같은 스키마라 실제 FK + 연관관계로 둔다.
 * {@code @ManyToOne}의 기본 fetch는 EAGER이므로 LAZY를 명시한다 —
 * 예약 목록을 읽을 때마다 상품을 건건이 조회하는 N+1이 되기 때문이다.
 */
@Getter
@Entity
@Table(name = "stock_reservation",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_reservation_order_id", columnNames = "order_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Builder
    private StockReservation(Long orderId, Product product, Long quantity) {
        this.orderId = orderId;
        this.product = product;
        this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
    }

    /**
     * 보상 처리로 재고를 되돌렸음을 기록한다.
     * 이미 되돌린 예약이면 상태를 그대로 두고 false를 반환해 호출부가 재고를 두 번 더하지 않게 한다.
     */
    public boolean restore() {
        if (status == ReservationStatus.RESTORED) {
            return false;
        }
        this.status = ReservationStatus.RESTORED;
        return true;
    }
}
