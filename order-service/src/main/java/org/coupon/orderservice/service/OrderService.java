package org.coupon.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.client.CouponChecker;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.dto.OrderResponse;
import org.coupon.orderservice.exception.OrderNotFoundException;
import org.coupon.orderservice.mapper.OrderMapper;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.saga.AbstractOrderSaga;
import org.coupon.orderservice.saga.OrderPlacementSaga;
import org.coupon.orderservice.saga.OrderSagaPayload;
import org.coupon.orderservice.saga.StockOnlyOrderSaga;
import org.coupon.orderservice.saga.framework.SagaManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final SagaManager sagaManager;
    private final CouponChecker couponChecker;

    @Transactional
    public OrderCreateResponse create(Long userId, OrderCreateRequest request) {
        // 없는 쿠폰·남의 쿠폰·이미 쓴 쿠폰은 주문을 받기 전에 걸러낸다 (실제 사용 처리는 사가가 한다, 쿠폰 서비스 장애면 서킷 브레이커가 503으로 거절)
        if (request.couponId() != null) {
            couponChecker.checkUsable(request.couponId());
        }

        Order order = Order.builder()
                .userId(userId)
                .couponId(request.couponId())
                .productId(request.productId())
                .quantity(request.quantity())
                .build();

        Order saved = orderRepository.save(order);
        OrderSagaPayload payload = OrderSagaPayload.of(saved);
        AbstractOrderSaga saga = saved.getCouponId() == null
                ? sagaManager.begin(saved.getId(), StockOnlyOrderSaga.class, payload, StockOnlyOrderSaga::new)
                : sagaManager.begin(saved.getId(), OrderPlacementSaga.class, payload, OrderPlacementSaga::new);

        log.info("주문 접수: orderId={}, sagaId={}, sagaType={}, userId={}, productId={}, quantity={}, couponId={}",
                saved.getId(), saga.getId(), saga.getType(), userId, request.productId(), request.quantity(),
                request.couponId());
        return orderMapper.toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findMyOrders(Long userId) {
        return orderRepository.findAllByUserIdWithItems(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findMyOrder(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
