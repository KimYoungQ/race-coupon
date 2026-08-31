package org.coupon.orderservice.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
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
public class OrderCouponSaga {

    static final String UNKNOWN_FAILURE = "UNKNOWN_FAILURE";

    private final OrderRepository orderRepository;
    private final CouponOutboxHelper couponOutboxHelper;
    private final ProductOutboxHelper productOutboxHelper;

    @Transactional
    public void couponApplied(CouponResponse response) {
        Optional<CouponOutbox> awaiting = couponOutboxHelper
                .findAwaiting(response.sagaId(), CouponOrderStatus.PENDING, SagaStatus.STARTED);
        if (awaiting.isEmpty()) {
            log.info("이미 처리된 쿠폰 적용 응답: sagaId={}", response.sagaId());
            return;
        }

        Order order = findOrder(response.orderId());
        order.complete(response.discountAmount(), response.finalAmount());
        awaiting.get().advance(order.getStatus(), SagaStatus.SUCCEEDED);

        closeProductLeg(response, order, SagaStatus.SUCCEEDED, StockOrderStatus.PENDING);

        log.info("쿠폰 적용 완료, 사가 성공: sagaId={}, orderId={}, 할인={}, 최종={}",
                response.sagaId(), order.getId(), order.getDiscountAmount(), order.getFinalAmount());
    }

    @Transactional
    public void couponFailed(CouponResponse response) {
        Optional<CouponOutbox> awaiting = couponOutboxHelper
                .findAwaiting(response.sagaId(), CouponOrderStatus.PENDING, SagaStatus.STARTED);
        if (awaiting.isEmpty()) {
            log.info("이미 처리된 쿠폰 실패 응답: sagaId={}", response.sagaId());
            return;
        }

        String failureCode = failureCodeOf(response.failureMessages());

        Order order = findOrder(response.orderId());
        order.startCompensation(failureCode);
        awaiting.get().advance(order.getStatus(), SagaStatus.FAILED);

        productOutboxHelper.saveProductOutboxMessage(
                order, StockOrderStatus.CANCELLED, SagaStatus.COMPENSATING);

        log.info("쿠폰 적용 실패, 재고 복구 요청 적재: sagaId={}, orderId={}, code={}",
                response.sagaId(), order.getId(), failureCode);
    }

    @Transactional
    public void couponRestored(CouponResponse response) {
        Optional<CouponOutbox> awaiting = couponOutboxHelper
                .findAwaiting(response.sagaId(), CouponOrderStatus.CANCELLED, SagaStatus.COMPENSATING);
        if (awaiting.isEmpty()) {
            log.warn("보낸 적 없는 쿠폰 복구 응답을 받았다. 사가 단계가 추가됐다면 이 경로를 설계할 것: "
                    + "sagaId={}, orderId={}", response.sagaId(), response.orderId());
            return;
        }

        Order order = findOrder(response.orderId());
        awaiting.get().advance(order.getStatus(), SagaStatus.COMPENSATED);
        log.info("쿠폰 복구 완료: sagaId={}, orderId={}", response.sagaId(), order.getId());
    }

    private void closeProductLeg(CouponResponse response, Order order,
                                 SagaStatus sagaStatus, StockOrderStatus requestStatus) {
        productOutboxHelper.find(response.sagaId(), requestStatus)
                .ifPresent(outbox -> outbox.advance(order.getStatus(), sagaStatus));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private String failureCodeOf(List<String> failureMessages) {
        return failureMessages.isEmpty() ? UNKNOWN_FAILURE : failureMessages.get(0);
    }
}
