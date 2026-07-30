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

/**
 * 발급 이력. "누가(userId) 어떤 쿠폰(couponId)을 언제(issuedAt) 받았고, 어느 주문에 썼는지"를 기록한다.
 * 발급 1건 = IssuedCoupon 1건 (Coupon 1 : N IssuedCoupon).
 * Phase 1은 학습 단순화를 위해 JPA 연관관계 대신 couponId 값 참조로 느슨하게 연결한다.
 *
 * <p><b>{@code UNIQUE(user_id, coupon_id)} — 사용자당 동일 쿠폰 한 장.</b>
 * 이 제약 하나가 세 가지를 한꺼번에 한다.
 * <ol>
 *   <li>발급 컨슈머가 저장 후 ack 전에 중단돼 같은 메시지를 다시 소비해도 row가 중복 생성되지 않는다</li>
 *   <li>주문 사가가 {@code (userId, couponId)}로 대상을 찾을 때 결과가 반드시 1건으로 확정된다</li>
 *   <li>따라서 클라이언트가 내부 PK를 몰라도 어느 발급 건을 쓸지 모호해지지 않는다</li>
 * </ol>
 * 제약을 엔티티에 선언하는 이유는 {@code ddl-auto: create}라 <b>DDL을 만드는 주체가 이 클래스</b>이기
 * 때문이다 — {@code docker/mysql/init/init.sql}에는 DATABASE 생성만 있고 테이블 정의가 없다.
 * (런타임 강제는 어디까지나 DB가 한다. JPA 선언 자체는 검사를 하지 않는다.)
 *
 * <p>{@code order_id}의 UNIQUE는 FK가 아니라 <b>멱등 키</b>다
 * ({@code StockReservation.orderId}와 같은 역할). 아직 쓰이지 않은 쿠폰은 null이고
 * MySQL은 UNIQUE 컬럼에 NULL을 여러 개 허용하므로 미사용 쿠폰끼리는 충돌하지 않는다.
 */
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

    /** 이 쿠폰을 사용한 주문. 미사용이면 null이다. */
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

    /**
     * 주문에 사용 처리한다.
     *
     * <p>할인액을 인자로 받지 않는 것은 의도적이다. 금액 계산의 권위는 {@link Coupon}에 있고
     * 이 엔티티는 "언제 어느 주문에 썼는지"만 기록한다. 권위를 두 곳으로 갈라놓지 않는다.
     *
     * <p>여기서 던지는 예외는 사가의 <b>최종 방어선</b>이다. 정상 경로에서는 참여자 서비스가
     * reply Outbox를 먼저 조회해 중복 요청을 걸러내므로 이 메서드가 두 번 불리지 않는다.
     *
     * @throws CouponNotOwnedException    다른 사용자의 쿠폰인 경우
     * @throws CouponAlreadyUsedException 이미 사용된 쿠폰인 경우
     */
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

    /**
     * 사용을 되돌린다. 이미 되돌렸거나 다른 주문이 쓴 쿠폰이면 아무것도 하지 않고 false를 반환해
     * 호출부가 복구를 두 번 처리하지 않게 한다 ({@code StockReservation.restore()}와 같은 계약).
     *
     * <p>현재 사가는 이 메서드를 호출하지 않는다 — 쿠폰 적용이 마지막 단계라 그 뒤에 실패할 스텝이 없고,
     * 명시적인 적용 실패 응답은 "적용된 적 없음"을 뜻하므로 되돌릴 것이 없다.
     * 적용 결과가 timeout으로 불확실해지거나 뒤에 새 단계가 생기면 그때 필요해진다.
     */
    public boolean restore(Long orderId) {
        if (status != IssuedCouponStatus.USED || !Objects.equals(this.orderId, orderId)) {
            return false;
        }
        this.status = IssuedCouponStatus.ISSUED;
        this.orderId = null;
        this.usedAt = null;
        return true;
    }

    /** 이 주문이 이미 사용한 쿠폰인지 — 중복 요청 판정용. */
    public boolean isUsedBy(Long orderId) {
        return status == IssuedCouponStatus.USED && Objects.equals(this.orderId, orderId);
    }
}
