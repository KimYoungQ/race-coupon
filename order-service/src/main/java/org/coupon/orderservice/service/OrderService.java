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

/**
 * 주문 생성·조회, 그리고 Saga의 <b>첫 단추</b>.
 *
 * <p>주문 접수는 ORDERS row를 만드는 것으로 끝나지 않는다. 재고 요청을 같은 트랜잭션에서
 * Outbox에 적재해야 "주문은 생겼는데 재고 요청은 영영 나가지 않는" 상태가 원천적으로 불가능해진다.
 * 이후 단계(응답 수신, 보상)는 {@code OrderProductSaga}/{@code OrderCouponSaga}가 맡는다.
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
    private final ProductOutboxHelper productOutboxHelper;

    /**
     * 주문을 CREATED로 접수하고 사가를 시작한다. 이 시점에 확정되는 것은 ORDERS row와
     * 재고 요청 Outbox row뿐이며 재고도 쿠폰도 아직 잡히지 않았다 — 그래서 응답이 201이 아니라 202다.
     *
     * <p><b>주문 저장과 Outbox 적재가 하나의 로컬 트랜잭션이다.</b> 여기서 Kafka를 직접 부르면
     * (dual write) 커밋에 실패한 주문의 재고 요청이 이미 나가버리거나, 반대로 커밋은 됐는데
     * 발행 직전에 프로세스가 죽어 요청이 영영 나가지 않는다. 둘 다 이 구조에서는 불가능하다.
     *
     * <p>실제 발행은 {@code ProductOutboxScheduler}의 폴링이 한다. 여기서 하는 일은 적재까지이고,
     * 그래서 접수와 발행 사이에 최대 폴링 주기만큼의 지연이 있다 — 응답이 202인 이유이기도 하다.
     *
     * @param userId 검증된 토큰의 sub. 요청 본문에서 받지 않는다 —
     *               이 값이 그대로 쿠폰 요청에 실려 소유자 판정 기준이 되므로 출처가 서버여야 한다
     */
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
