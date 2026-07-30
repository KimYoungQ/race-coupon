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

/**
 * 쿠폰 응답을 받아 사가를 종료하거나 보상을 시작한다. <b>사가의 마지막 단계</b>다.
 *
 * <p>{@link OrderProductSaga}와 대칭이지만 한 가지가 다르다 — 여기서 실패하면
 * <b>이미 차감된 재고가 있으므로 되돌려야 한다.</b> 그 보상 요청을 만드는 것이 이 클래스의 몫이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCouponSaga {

    /** 참여자가 실패 코드를 안 실어 보낸 경우. 이유 없는 실패로 남기지 않기 위한 값이다. */
    static final String UNKNOWN_FAILURE = "UNKNOWN_FAILURE";

    private final OrderRepository orderRepository;
    private final CouponOutboxHelper couponOutboxHelper;
    private final ProductOutboxHelper productOutboxHelper;

    /**
     * 쿠폰 적용 성공 = 사가 성공. 응답이 실어온 할인·최종 금액을 주문에 확정한다.
     *
     * <p>{@code OrderStatus}에 {@code COUPON_APPLIED} 같은 중간 상태를 두지 않는다 —
     * 쿠폰 적용이 마지막 단계라 곧바로 {@code COMPLETED}가 되고, 값이 바뀌지 않는 상태를
     * 사용자에게 노출할 이유가 없다.
     */
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

        // 재고 Outbox는 PROCESSING에 멈춰 있다. 여기서 올려주지 않으면
        //        // 사가는 끝났는데 그 row만 영원히 진행 중으로 남아 Cleaner가 지우지 못한다.
        closeProductLeg(response, order, SagaStatus.SUCCEEDED, StockOrderStatus.PENDING);

        log.info("쿠폰 적용 완료, 사가 성공: sagaId={}, orderId={}, 할인={}, 최종={}",
                response.sagaId(), order.getId(), order.getDiscountAmount(), order.getFinalAmount());
    }

    /**
     * 쿠폰 적용 실패 = <b>보상 시작</b>. 이미 예약된 재고를 되돌려야 한다.
     *
     * <p>복구 요청은 정상 예약과 <b>같은 토픽</b>으로 나가고 {@code CANCELLED}로만 갈린다.
     * Outbox row도 {@code UNIQUE(saga_id, request_status)} 덕분에 정상 요청과 공존한다 —
     * {@code request_status}가 없으면 이 INSERT가 충돌해 보상이 영원히 발행되지 않는다.
     */
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
        // 진짜 실패 원인은 여기서 기록된다. 보상이 끝날 때 completeCompensation()이 이 값을 유지한다.
        order.startCompensation(failureCode);
        awaiting.get().advance(order.getStatus(), SagaStatus.FAILED);

        // 보상 요청 적재. 발행은 스케줄러가 한다.
        productOutboxHelper.saveProductOutboxMessage(
                order, StockOrderStatus.CANCELLED, SagaStatus.COMPENSATING);

        log.info("쿠폰 적용 실패, 재고 복구 요청 적재: sagaId={}, orderId={}, code={}",
                response.sagaId(), order.getId(), failureCode);
    }

    /**
     * 쿠폰 복구 성공.
     *
     * <p><b>현재 사가에서는 도달하지 않는다.</b> 쿠폰 적용이 마지막 단계라 그 뒤에 실패할 스텝이 없고,
     * 명시적인 적용 실패는 "적용된 적 없음"을 뜻하므로 복구 요청을 보낼 일이 없다.
     * 그럼에도 분기를 비워 두지 않는 것은, 나중에 단계가 추가돼 이 응답이 실제로 오기 시작했을 때
     * <b>조용히 무시되는 대신 로그로 드러나게</b> 하기 위해서다.
     */
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

    /**
     * 재고 leg의 Outbox 상태를 사가 종료에 맞춘다.
     *
     * <p>없어도 사가 자체는 끝나지만, 그 row가 종료 상태에 이르지 못해 Cleaner의 대상에서 빠진다.
     *
     * <p><b>{@code save()}가 없어도 반영된다.</b> 이 메서드는 {@code @Transactional} 안에서 불리므로
     * 조회 결과가 영속 상태이고, {@code advance()}로 바꾼 값은 커밋 시점에 더티 체킹이 UPDATE로 내보낸다.
     * 새로 만드는 엔티티가 아니라 이미 있는 row를 고치는 것이라 명시적 저장이 필요 없다.
     * (반대로 발행 콜백처럼 트랜잭션 밖에서 다루는 경우에는 detached라 반영되지 않는다 —
     * 그래서 {@code OutboxPublishState}는 자기 트랜잭션에서 다시 읽는다.)
     */
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
