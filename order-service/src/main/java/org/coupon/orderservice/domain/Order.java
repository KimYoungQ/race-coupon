package org.coupon.orderservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 주문이자 Saga 인스턴스. 재고 예약 → 쿠폰 사용으로 이어지는 사가의 진행 상태를 이 row가 보관한다.
 *
 * <p><b>상태 전이는 이 도메인이 스스로 검증한다.</b> 허용되지 않은 상태에서의 전이는
 * {@link InvalidOrderStateException}으로 거부된다. 이 가드는 불변식 보호인 동시에
 * Kafka 리플라이가 중복 수신됐을 때의 멱등성 방어를 겸한다 — 이미 COMPLETED가 된 주문에
 * 같은 리플라이가 한 번 더 도착해도 금액이 두 번 반영되지 않는다.
 *
 * <p>{@code user_id}·{@code coupon_id}는 다른 서비스의 스키마를 가리키므로
 * FK도 연관관계도 만들 수 없고 {@code Long} 값 참조만 가능하다.
 */
@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Saga 상관 키. 주문 생성 시 발급되며 이후 불변이다.
     *
     * <p>{@code orderId}와 역할이 다르다 — {@code orderId}는 도메인 조회 키이고,
     * {@code sagaId}는 <b>Outbox 조회의 주 키</b>다. 응답이 도착하면
     * {@code sagaId + requestStatus + 기대 SagaStatus}로 수용 가능한 Outbox row를 찾는다.
     *
     * <p>BINARY(16) 대신 CHAR(36)으로 저장한다. 사가 진행 상황을 SQL로 눈으로 좇는 일이 잦은데
     * 바이너리로 두면 읽을 수가 없다.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private UUID sagaId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /**
     * 사용할 쿠폰 템플릿. null이면 쿠폰 없는 주문이라 사가가 재고 예약 후 바로 완료된다.
     *
     * <p>발급 row의 PK(issuedCouponId)가 아니라 <b>쿠폰 템플릿 ID</b>다. 발급 API가 그 PK를
     * 어디서도 돌려주지 않아 클라이언트가 알 수 없고, 발급 row 자체도 Kafka 컨슈머가
     * 비동기로 나중에 만든다. 어느 발급 row를 쓸지는 coupon-service가
     * {@code (userId, couponId, ISSUED)}로 해석한다.
     */
    private Long couponId;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private Long finalAmount;

    /** 실패 원인. 성공한 주문에서는 null이다. */
    private String failureCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * 주문 생성 시점에 확정되는 것은 "누가 어떤 상품을 몇 개" 뿐이다.
     * 금액은 재고 예약 리플라이가 단가를 실어올 때까지 알 수 없어 0으로 시작한다.
     *
     * <p>품목을 빌더 인자가 아니라 여기서 직접 만든다 — 1주문 1상품 불변식을
     * 생성 시점에 고정해야 이후 어느 경로로도 품목 0개·2개인 주문이 생기지 않는다.
     */
    @Builder
    private Order(Long userId, Long couponId, Long productId, Integer quantity) {
        this.sagaId = UUID.randomUUID();
        this.userId = userId;
        this.couponId = couponId;
        this.status = OrderStatus.CREATED;
        this.totalAmount = 0L;
        this.discountAmount = 0L;
        this.finalAmount = 0L;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.items.add(OrderItem.builder()
                .order(this)
                .productId(productId)
                .quantity(quantity)
                .build());
    }

    /**
     * 재고 예약 성공. 리플라이가 실어온 상품명·단가를 품목에 고정하고 주문 총액을 확정한다.
     *
     * <p>총액을 인자로 받지 않고 {@code unitPrice * quantity}로 직접 계산한다.
     * 수량을 아는 쪽이 이 객체이므로 계산을 밖에 두면 곱셈이 어긋나도 도메인이 알 수 없다.
     */
    public void reserveStock(String productName, long unitPrice) {
        requireStatus(OrderStatus.CREATED);
        OrderItem item = primaryItem();
        item.applyPriceSnapshot(productName, unitPrice);
        this.totalAmount = unitPrice * item.getQuantity();
        this.finalAmount = this.totalAmount;
        this.status = OrderStatus.STOCK_RESERVED;
        touch();
    }

    /**
     * 사가 성공.
     *
     * <p>CREATED에서의 완료를 <b>허용하지 않는다.</b> 쿠폰 없는 주문도 반드시
     * {@link #reserveStock(String, long)}을 먼저 거치므로 항상 STOCK_RESERVED를 지난다.
     * CREATED를 허용하면 "재고 예약 없이 완료"라는 경로가 열리고, 그 경로로 들어온 주문은
     * {@code productName=null, unitPrice=null, totalAmount=0}인 채로 COMPLETED가 된다 —
     * 도메인이 막아야 할 것을 허용하는 셈이다.
     */
    public void complete(long discountAmount, long finalAmount) {
        requireStatus(OrderStatus.STOCK_RESERVED);
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.status = OrderStatus.COMPLETED;
        touch();
    }

    /** 보상 시작. 이미 차감된 재고를 되돌리는 동안의 상태다. */
    public void startCompensation(String failureCode) {
        requireStatus(OrderStatus.CREATED, OrderStatus.STOCK_RESERVED);
        this.failureCode = failureCode;
        this.status = OrderStatus.COMPENSATING;
        touch();
    }

    /**
     * 최종 실패. 재고 예약 자체가 실패해 되돌릴 것이 없는 경우(CREATED)와
     * 보상을 마친 경우(COMPENSATING) 양쪽에서 도달한다.
     *
     * <p><b>주의 — COMPENSATING에서 이 메서드를 부르면 실패 원인이 덮인다.</b>
     * {@link #startCompensation(String)}이 기록해 둔 진짜 원인("쿠폰이 이미 사용됨")이
     * 뒷정리 결과("재고 복구됨")로 바뀌어 사용자도 운영자도 이유를 알 수 없게 된다.
     * 보상을 마친 뒤의 종료에는 {@link #completeCompensation()}를 쓴다 —
     * 그쪽은 실패 원인을 건드리지 않는다.
     *
     * <p>두 경로가 공존하므로 규율은 호출부가 지킨다. 사가 응답 처리에서
     * {@code StockStatus.RESTORED}를 받았을 때 이 메서드를 부르지 않는지 확인할 것.
     */
    public void fail(String failureCode) {
        requireStatus(OrderStatus.CREATED, OrderStatus.COMPENSATING);
        this.failureCode = failureCode;
        this.status = OrderStatus.FAILED;
        touch();
    }

    /**
     * 보상을 마치고 최종 실패로 확정한다.
     *
     * <p>실패 원인을 인자로 받지 않는다. 사가를 무너뜨린 진짜 원인은 보상을 <i>시작할 때</i>
     * 이미 {@link #startCompensation(String)}이 기록했고, 보상 자체는 그 뒷정리일 뿐이다.
     * 여기서 새 코드를 받으면 "쿠폰이 이미 사용됨" 같은 원인이 "재고 복구됨"으로 덮여
     * 사용자도 운영자도 왜 실패했는지 알 수 없게 된다.
     */
    public void completeCompensation() {
        requireStatus(OrderStatus.COMPENSATING);
        this.status = OrderStatus.FAILED;
        touch();
    }

    /** 1주문 1상품이 확정이므로 품목은 항상 정확히 하나다(생성자가 보장). */
    public OrderItem primaryItem() {
        return items.get(0);
    }

    private void requireStatus(OrderStatus... allowed) {
        for (OrderStatus candidate : allowed) {
            if (this.status == candidate) {
                return;
            }
        }
        throw new InvalidOrderStateException(this.status);
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
