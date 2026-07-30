package org.coupon.orderservice.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.OrderStatus;

import java.util.UUID;

/**
 * product-service로 나갈 재고 요청의 Outbox. 이름은 <b>상대 서비스</b> 기준이다 —
 * 참여자 쪽 Outbox가 {@code order_outbox}인 것과 같은 규칙이라 채널 하나를 두 이름으로 부르지 않는다.
 *
 * <p><b>{@code UNIQUE(saga_id, request_status)}가 이 설계의 핵심이다.</b>
 * 정상 예약과 보상 복구가 같은 sagaId를 공유하므로, {@code request_status} 없이
 * {@code UNIQUE(saga_id)}만 걸면 보상 요청 INSERT가 정상 요청과 충돌해
 * <b>보상이 영원히 발행되지 않는다.</b> 정상 시나리오 테스트로는 절대 드러나지 않는 종류의 버그다.
 * 두 row는 이렇게 공존한다.
 * <pre>
 *   (sagaId=X, PENDING)    정상 예약 요청
 *   (sagaId=X, CANCELLED)  보상 복구 요청
 * </pre>
 */
@Getter
@Entity
@Table(name = "product_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_outbox_saga_request",
                columnNames = {"saga_id", "request_status"}),
        indexes = @Index(
                name = "idx_product_outbox_poll",
                columnList = "type, outbox_status, saga_status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOutbox extends OrchestratorOutbox {

    /**
     * payload에 담긴 {@code StockOrderStatus}의 사본.
     *
     * <p>payload는 JSON 문자열이라 SQL로 조회할 수 없다. 멱등 조회와 UNIQUE 제약이
     * 정상 요청과 보상 요청을 구분하려면 컬럼으로 꺼내 둬야 한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false)
    private StockOrderStatus requestStatus;

    @Builder
    private ProductOutbox(UUID id, UUID sagaId, Long orderId, String type, String payload,
                          OrderStatus orderStatus, SagaStatus sagaStatus, StockOrderStatus requestStatus) {
        super(id, sagaId, orderId, type, payload, orderStatus, sagaStatus);
        this.requestStatus = requestStatus;
    }
}
