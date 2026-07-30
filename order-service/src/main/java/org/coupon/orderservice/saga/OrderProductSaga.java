package org.coupon.orderservice.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.event.StockResponse;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.coupon.orderservice.exception.OrderNotFoundException;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.service.outbox.CouponOutboxHelper;
import org.coupon.orderservice.service.outbox.ProductOutboxHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 재고 응답을 받아 사가를 전진시키거나 종료한다.
 *
 * <p>세 메서드가 곧 {@code StockStatus}의 세 값이다. 토픽이 아니라 응답 DTO의 상태로 갈린다.
 *
 * <p><b>모든 진입점이 기대 Outbox 조회로 시작한다.</b> 비어 있으면 이미 처리했거나 늦게 도착한
 * 응답이므로 예외 없이 종료한다 — 이것이 멱등성의 1차 방어선이다. 조회 조건에
 * {@code requestStatus}가 들어가는 이유는 정상 예약과 보상 복구가 같은 sagaId를 공유하기 때문이다.
 *
 * <p><b>다음 단계는 '호출'이 아니라 '기록'이다.</b> 여기서 Kafka를 부르지 않고 Outbox row만 남긴다.
 * 그래야 주문 상태 변경과 다음 요청이 하나의 로컬 트랜잭션으로 묶인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProductSaga {

    /** 참여자가 실패 코드를 안 실어 보낸 경우. 이유 없는 실패로 남기지 않기 위한 값이다. */
    static final String UNKNOWN_FAILURE = "UNKNOWN_FAILURE";

    private final OrderRepository orderRepository;
    private final ProductOutboxHelper productOutboxHelper;
    private final CouponOutboxHelper couponOutboxHelper;

    /**
     * 재고 예약 성공. 응답이 실어온 단가로 주문 총액을 확정하고 다음 단계를 준비한다.
     */
    @Transactional
    public void stockReserved(StockResponse response) {
        Optional<ProductOutbox> awaiting = productOutboxHelper
                .findAwaiting(response.sagaId(), StockOrderStatus.PENDING, SagaStatus.STARTED);
        if (awaiting.isEmpty()) {
            log.info("이미 처리된 재고 예약 응답: sagaId={}", response.sagaId());
            return;
        }
        ProductOutbox outbox = awaiting.get();

        Order order = findOrder(response.orderId());
        order.reserveStock(response.productName(), response.unitPrice());
        outbox.advance(order.getStatus(), SagaStatus.PROCESSING);

        // 쿠폰이 없으면 사가는 여기서 끝난다. 쿠폰 Outbox를 만들지 않는다.
        if (order.getCouponId() == null) {
            order.complete(0L, order.getTotalAmount());
            outbox.advance(order.getStatus(), SagaStatus.SUCCEEDED);
            log.info("쿠폰 없는 주문 완료: sagaId={}, orderId={}, 금액={}",
                    response.sagaId(), order.getId(), order.getFinalAmount());
            return;
        }

        // 다음 단계는 '기록'이다 — 발행은 스케줄러가 한다
        couponOutboxHelper.saveCouponOutboxMessage(order, CouponOrderStatus.PENDING, SagaStatus.STARTED);
        log.info("재고 예약 완료, 쿠폰 적용 요청 적재: sagaId={}, orderId={}, 총액={}",
                response.sagaId(), order.getId(), order.getTotalAmount());
    }

    /**
     * 재고 처리 실패. 응답만으로는 <b>예약 실패인지 복구 실패인지 알 수 없어</b>
     * 어느 Outbox가 응답을 기다리고 있었는지로 판별한다.
     */
    @Transactional
    public void stockFailed(StockResponse response) {
        String failureCode = failureCodeOf(response.failureMessages());

        // 예약 실패 — 아직 아무것도 차감되지 않았으니 되돌릴 것이 없다
        Optional<ProductOutbox> reserving = productOutboxHelper
                .findAwaiting(response.sagaId(), StockOrderStatus.PENDING, SagaStatus.STARTED);
        if (reserving.isPresent()) {
            Order order = findOrder(response.orderId());
            order.fail(failureCode);
            reserving.get().advance(order.getStatus(), SagaStatus.FAILED);
            log.info("재고 예약 실패로 주문 종료: sagaId={}, orderId={}, code={}",
                    response.sagaId(), order.getId(), failureCode);
            return;
        }

        // 복구 실패 — 보상이 끝나지 못했다. 자동으로 회복할 방법이 없다.
        Optional<ProductOutbox> compensating = productOutboxHelper
                .findAwaiting(response.sagaId(), StockOrderStatus.CANCELLED, SagaStatus.COMPENSATING);
        if (compensating.isPresent()) {
            log.error("재고 복구 실패. 주문이 COMPENSATING에 멈춘다 — 사람이 봐야 한다: "
                            + "sagaId={}, orderId={}, code={}",
                    response.sagaId(), response.orderId(), failureCode);
            return;
        }

        log.info("이미 처리된 재고 실패 응답: sagaId={}", response.sagaId());
    }

    /**
     * 재고 복구 성공. 보상이 끝났으므로 주문을 최종 실패로 확정한다.
     *
     * <p>{@code fail()}이 아니라 {@code completeCompensation()}을 쓴다 —
     * {@code fail()}은 실패 원인을 인자로 받아 덮어쓰므로, 사가를 무너뜨린 진짜 원인
     * ("쿠폰이 이미 사용됨")이 뒷정리 결과로 바뀐다.
     */
    @Transactional
    public void stockRestored(StockResponse response) {
        Optional<ProductOutbox> awaiting = productOutboxHelper
                .findAwaiting(response.sagaId(), StockOrderStatus.CANCELLED, SagaStatus.COMPENSATING);
        if (awaiting.isEmpty()) {
            log.info("이미 처리된 재고 복구 응답: sagaId={}", response.sagaId());
            return;
        }

        Order order = findOrder(response.orderId());
        order.completeCompensation();
        awaiting.get().advance(order.getStatus(), SagaStatus.COMPENSATED);

        log.info("보상 완료, 주문 최종 실패: sagaId={}, orderId={}, code={}",
                response.sagaId(), order.getId(), order.getFailureCode());
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * 참여자가 실패 코드를 하나 실어 보낸다. 비어 있으면 원인을 특정할 수 없다는 뜻이므로
     * 그 사실 자체를 코드로 남긴다 — 빈 문자열이나 null을 넣으면 조회 화면에서 "실패인데 이유 없음"이 된다.
     */
    private String failureCodeOf(List<String> failureMessages) {
        return failureMessages.isEmpty() ? UNKNOWN_FAILURE : failureMessages.get(0);
    }
}
