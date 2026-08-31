package org.coupon.couponservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Long totalQuantity;

    @Column(nullable = false)
    private Long issuedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(nullable = false)
    private Long discountValue;

    private Long maxDiscountAmount;

    private Long minOrderAmount;

    @Column(nullable = false)
    private LocalDateTime eventEndAt;

    @Builder
    private Coupon(String title, Long totalQuantity, DiscountType discountType, Long discountValue,
                   Long maxDiscountAmount, Long minOrderAmount, LocalDateTime eventEndAt) {
        discountType.validate(discountValue);
        this.title = title;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0L;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = minOrderAmount;
        this.eventEndAt = eventEndAt;
    }

    public Long remaining() {
        return totalQuantity - issuedQuantity;
    }

    public long finalPrice(long price) {
        long discount = DiscountPolicyFactory.create(this).discount(price);
        return Math.max(0, price - discount);
    }

    public long discountFor(long price) {
        return price - finalPrice(price);
    }

    public boolean satisfiesMinOrderAmount(long price) {
        return minOrderAmount == null || price >= minOrderAmount;
    }
}
