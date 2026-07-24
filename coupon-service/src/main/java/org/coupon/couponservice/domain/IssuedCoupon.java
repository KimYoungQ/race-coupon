package org.coupon.couponservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발급 이력. "누가(userId) 어떤 쿠폰(couponId)을 언제(issuedAt) 받았는지" 1건을 기록한다.
 * 발급 1건 = IssuedCoupon 1건 (Coupon 1 : N IssuedCoupon).
 * Phase 1은 학습 단순화를 위해 JPA 연관관계 대신 couponId 값 참조로 느슨하게 연결한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private IssuedCoupon(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.issuedAt = LocalDateTime.now();
    }

    public static IssuedCoupon create(Long userId, Long couponId) {
        return new IssuedCoupon(userId, couponId);
    }
}
