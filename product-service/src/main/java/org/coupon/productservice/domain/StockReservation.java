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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stock_reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private Long quantity;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Builder
    private StockReservation(Long orderId, Product product, Long quantity) {
        this.orderId = orderId;
        this.product = product;
        this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
    }

    public boolean restore() {
        if (status == ReservationStatus.RESTORED) {
            return false;
        }
        this.status = ReservationStatus.RESTORED;
        return true;
    }
}
