package org.coupon.racecoupon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.racecoupon.exception.CouponSoldOutException;

/**
 * 재고를 가진 쿠폰. 비관적 락(SELECT ... FOR UPDATE)의 잠금 대상 row다.
 * 발급 규칙(불변식 0 <= issuedQuantity <= totalQuantity)을 스스로 지킨다.
 */
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

    private Coupon(String title, Long totalQuantity) {
        this.title = title;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0L;
    }

    public static Coupon create(String title, Long totalQuantity) {
        return new Coupon(title, totalQuantity);
    }

    /**
     * 재고가 남아 있으면 발급 수량을 1 증가시킨다.
     * read(issuedQuantity) -> +1 -> write 구간이 레이스 컨디션의 물리적 지점이다.
     *
     * @throws CouponSoldOutException 재고가 모두 소진된 경우
     */
    public void issue() {
        if (issuedQuantity >= totalQuantity) {
            throw new CouponSoldOutException();
        }
        this.issuedQuantity++;
    }

    /**
     * 잔여 발급 가능 수량.
     */
    public Long remaining() {
        return totalQuantity - issuedQuantity;
    }
}
