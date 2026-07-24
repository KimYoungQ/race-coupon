package org.coupon.couponservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.couponservice.exception.CouponSoldOutException;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(nullable = false)
    private Long discountValue;

    /** 최대 할인 한도(선택). null이면 한도 없음. */
    private Long maxDiscountAmount;

    /** 최소 주문 금액(선택). null이면 조건 없음. */
    private Long minOrderAmount;

    private Coupon(String title, Long totalQuantity, DiscountType discountType, Long discountValue,
                   Long maxDiscountAmount, Long minOrderAmount) {
        discountType.validate(discountValue);
        this.title = title;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0L;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = minOrderAmount;
    }

    public static Coupon create(String title, Long totalQuantity, DiscountType discountType, Long discountValue) {
        return new Coupon(title, totalQuantity, discountType, discountValue, null, null);
    }

    public static Coupon create(String title, Long totalQuantity, DiscountType discountType, Long discountValue,
                                Long maxDiscountAmount, Long minOrderAmount) {
        return new Coupon(title, totalQuantity, discountType, discountValue, maxDiscountAmount, minOrderAmount);
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

    /**
     * 이 쿠폰을 적용했을 때의 최종 결제가(0 하한).
     * 유형(전략)과 조건(데코레이터) 조립은 {@link DiscountPolicyFactory}가 담당하고, 0 하한만 도메인이 보장한다.
     */
    public long finalPrice(long price) {
        long discount = DiscountPolicyFactory.create(this).discount(price);
        return Math.max(0, price - discount);
    }
}
