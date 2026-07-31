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

/**
 * 재고를 가진 쿠폰.
 *
 * <p>발급 수량 판정은 이 엔티티가 하지 않는다 — V4에서는 Redis Lua가 확정하고,
 * {@code issuedQuantity}는 컨슈머가 뒤이어 올리는 관찰값이다.
 * 불변식을 스스로 지키던 {@code issue()}는 V1·V2 전용이라 주석으로 보존돼 있다.
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

    @Column(nullable = false)
    private LocalDateTime eventEndAt;

    /**
     * maxDiscountAmount·minOrderAmount는 생략 가능하다(빌더 기본값 null = 조건 없음).
     */
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

    /*
    V1·V2 전용. 재고가 남아 있으면 발급 수량을 1 증가시킨다.
    read(issuedQuantity) -> +1 -> write 구간이 레이스 컨디션의 물리적 지점이다 — 이 프로젝트의 출발점.

    V4에서는 부르지 않는다. 수량 판정은 Redis Lua가 끝내고, issuedQuantity는 컨슈머가
    CouponRepositoryCustom.increaseIssuedQuantity(조건 없는 원자적 UPDATE)로 올린다.
    컨슈머가 이걸 부르면 Redis와 DB 카운터가 어긋나는 순간 여기서 예외가 터지는데,
    발급 리스너에는 DLT가 없어 그 메시지가 영원히 재시도된다.

    되살리려면 org.coupon.couponservice.exception.CouponSoldOutException import도 함께 복구한다.

    public void issue() {
        if (issuedQuantity >= totalQuantity) {
            throw new CouponSoldOutException();
        }
        this.issuedQuantity++;
    }
    */

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

    /**
     * 이 쿠폰의 <b>실효</b> 할인액. {@link #finalPrice(long)}와 항상 정합한다.
     *
     * <p>{@code DiscountPolicyFactory.create(this).discount(price)}를 직접 쓰면 안 된다 —
     * 그 값에는 0 하한이 없어서 원가를 넘어설 수 있다. 예를 들어 원가 1,000원에
     * 5,000원 정액 쿠폰이면 정책은 5000을 돌려주지만 실제 최종가는 0원이다.
     * 사가 응답에 {@code discountAmount}와 {@code finalAmount}를 함께 실어야 하는데
     * 그대로 실으면 {@code total=1000, discount=5000, final=0}이라는 맞지 않는 조합이 나간다.
     */
    public long discountFor(long price) {
        return price - finalPrice(price);
    }

    /**
     * 최소 주문 금액 충족 여부.
     *
     * <p>{@link MinOrderAmountDecorator}는 미달 시 예외 없이 할인 0을 돌려준다. 그 동작은
     * "쿠폰을 적용하면 얼마인가" 조회에는 맞지만 <b>사가에서는 맞지 않는다</b> —
     * 조용히 할인 0으로 주문이 완료되면 사용자는 이유를 모르는데 쿠폰은 소진된다.
     * 사가는 이 메서드로 먼저 판정해 명시적으로 실패시킨다. 데코레이터는 그대로 둔다.
     */
    public boolean satisfiesMinOrderAmount(long price) {
        return minOrderAmount == null || price >= minOrderAmount;
    }
}
