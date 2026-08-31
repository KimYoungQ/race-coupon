package org.coupon.couponservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.couponservice.exception.CouponAlreadyUsedException;
import org.coupon.couponservice.exception.CouponNotOwnedException;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(name = "issued_coupon",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_issued_coupon_user_coupon",
                columnNames = {"user_id", "coupon_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuedCouponStatus status;

    @Column(name = "order_id", unique = true)
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    @Builder
    private IssuedCoupon(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = IssuedCouponStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }

    public void use(Long orderId, Long userId) {
        if (!Objects.equals(this.userId, userId)) {
            throw new CouponNotOwnedException(id);
        }
        if (status == IssuedCouponStatus.USED) {
            throw new CouponAlreadyUsedException(id);
        }
        this.status = IssuedCouponStatus.USED;
        this.orderId = orderId;
        this.usedAt = LocalDateTime.now();
    }

    public boolean restore(Long orderId) {
        if (status != IssuedCouponStatus.USED || !Objects.equals(this.orderId, orderId)) {
            return false;
        }
        this.status = IssuedCouponStatus.ISSUED;
        this.orderId = null;
        this.usedAt = null;
        return true;
    }

    public boolean isUsedBy(Long orderId) {
        return status == IssuedCouponStatus.USED && Objects.equals(this.orderId, orderId);
    }
}
