package org.coupon.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.dto.OrderResponse;
import org.coupon.orderservice.exception.OrderNotFoundException;
import org.coupon.orderservice.mapper.OrderMapper;
import org.coupon.orderservice.repository.OrderRepository;
import org.coupon.orderservice.service.outbox.ProductOutboxHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductOutboxHelper productOutboxHelper;

    @Transactional
    public OrderCreateResponse create(Long userId, OrderCreateRequest request) {
        Order order = Order.builder()
                .userId(userId)
                .couponId(request.couponId())
                .productId(request.productId())
                .quantity(request.quantity())
                .build();

        Order saved = orderRepository.save(order);
        productOutboxHelper.saveProductOutboxMessage(saved, StockOrderStatus.PENDING, SagaStatus.STARTED);

        log.info("주문 접수: orderId={}, sagaId={}, userId={}, productId={}, quantity={}",
                saved.getId(), saved.getSagaId(), userId, request.productId(), request.quantity());
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
