package org.coupon.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.domain.Order;
import org.coupon.orderservice.dto.OrderCreateRequest;
import org.coupon.orderservice.dto.OrderCreateResponse;
import org.coupon.orderservice.dto.OrderResponse;
import org.coupon.orderservice.exception.OrderNotFoundException;
import org.coupon.orderservice.mapper.OrderMapper;
import org.coupon.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 생성·조회. 재고 예약과 쿠폰 사용을 잇는 Saga 오케스트레이션은
 * 다음 웨이브의 {@code OrderSagaService}가 맡는다.
 *
 * <p>{@code open-in-view: false}라 DTO 변환을 반드시 트랜잭션 안에서 끝낸다.
 * 컨트롤러까지 엔티티를 들고 나가면 LAZY 품목 접근이 LazyInitializationException이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    /**
     * 주문을 CREATED로 접수한다. 이 시점에 확정되는 것은 ORDERS row 한 줄뿐이며
     * 재고도 쿠폰도 아직 잡히지 않았다 — 그래서 응답이 201이 아니라 202다.
     *
     * @param userId 검증된 토큰의 sub. 요청 본문에서 받지 않는다
     */
    @Transactional
    public OrderCreateResponse create(Long userId, OrderCreateRequest request) {
        Order order = Order.builder()
                .userId(userId)
                .issuedCouponId(request.issuedCouponId())
                .productId(request.productId())
                .quantity(request.quantity())
                .build();

        Order saved = orderRepository.save(order);
        log.info("주문 접수: orderId={}, userId={}, productId={}, quantity={}",
                saved.getId(), userId, request.productId(), request.quantity());
        return orderMapper.toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findMyOrders(Long userId) {
        return orderRepository.findAllByUserIdWithItems(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    /**
     * 내 주문 단건. 소유자가 다르면 조회 결과가 비어 404가 된다.
     * 403을 주면 "그 orderId는 존재한다"는 사실이 새어 남의 주문 ID를 열거할 수 있다.
     */
    @Transactional(readOnly = true)
    public OrderResponse findMyOrder(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
