package org.coupon.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.productservice.exception.ProductOutOfStockException;

import java.time.LocalDateTime;

/**
 * 재고를 가진 상품. 이 프로젝트의 두 번째 동시성 지점이다.
 *
 * <p>{@link #decrease(long)}의 read(stock) -> -n -> write 구간이 물리적인 레이스 지점이지만
 * 여기에 DB 락을 걸지 않는다. 재고 차감 커맨드가 {@code productId}를 파티션 키로 발행되어
 * 같은 상품의 차감이 한 파티션에서 직렬화되기 때문이다. 경합이 없는 락은 순수 비용이고,
 * 락을 겹치면 파티션 전략이 실제로 동작하는지 검증할 수 없게 된다.
 *
 * <p>이 전제는 "재고 변경 경로가 Kafka 컨슈머 하나뿐"일 때만 성립한다.
 * 관리자 재고 수정 API를 두지 않는 이유이며, 나중에 필요해져도 커맨드 발행 형태여야 한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 단가(원). 주문 금액의 권위는 상품 서비스에 있고, 주문에는 시점 스냅샷으로 전달된다. */
    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Long stock;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Product(String name, Long price, Long stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 재고를 차감한다. 불변식 {@code 0 <= stock}을 도메인이 스스로 지킨다.
     *
     * @throws ProductOutOfStockException 남은 재고가 요청 수량보다 적은 경우
     */
    public void decrease(long quantity) {
        if (stock < quantity) {
            throw new ProductOutOfStockException(id, stock, quantity);
        }
        this.stock -= quantity;
    }

    /**
     * 차감했던 재고를 되돌린다. Saga 보상(쿠폰 사용 실패)에서만 호출된다.
     * 차감분을 그대로 더하므로 상한 검사가 필요 없다.
     */
    public void restore(long quantity) {
        this.stock += quantity;
    }
}
