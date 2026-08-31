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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProductSaga {

    static final String UNKNOWN_FAILURE = "UNKNOWN_FAILURE";

    private final OrderRepository orderRepository;
    private final ProductOutboxHelper productOutboxHelper;
    private final CouponOutboxHelper couponOutboxHelper;

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

        if (order.getCouponId() == null) {
            order.complete(0L, order.getTotalAmount());
            outbox.advance(order.getStatus(), SagaStatus.SUCCEEDED);
            log.info("쿠폰 없는 주문 완료: sagaId={}, orderId={}, 금액={}",
                    response.sagaId(), order.getId(), order.getFinalAmount());
            return;
        }

        couponOutboxHelper.saveCouponOutboxMessage(order, CouponOrderStatus.PENDING, SagaStatus.STARTED);
        log.info("재고 예약 완료, 쿠폰 적용 요청 적재: sagaId={}, orderId={}, 총액={}",
                response.sagaId(), order.getId(), order.getTotalAmount());
    }

    @Transactional
    public void stockFailed(StockResponse response) {
        String failureCode = failureCodeOf(response.failureMessages());

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

    private String failureCodeOf(List<String> failureMessages) {
        return failureMessages.isEmpty() ? UNKNOWN_FAILURE : failureMessages.get(0);
    }
}
