package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.domain.outbox.OrderOutbox;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.coupon.couponservice.service.outbox.OrderOutboxHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 쿠폰 요청을 처리하고 결과를 응답 Outbox에 남긴다. 사가에서 coupon-service가 맡은 전부다.
 *
 * <p>구조는 {@code StockSagaService}와 같고 두 가지가 더 있다.
 * <ol>
 *   <li><b>요청이 내부 PK가 아니라 {@code couponId}를 실어 온다.</b> 발급 API가 발급 row의 PK를
 *       돌려주지 않고, row 자체도 컨슈머가 비동기로 나중에 만들기 때문이다.
 *       어느 발급 건을 쓸지는 여기서 {@code (userId, couponId)}로 해석한다 —
 *       {@code UNIQUE(user_id, coupon_id)}가 그 결과를 1건으로 확정한다</li>
 *   <li><b>금액 계산이 붙는다.</b> {@code discountAmount}와 {@code finalAmount}를 둘 다 실어 보내야
 *       하는데, 서로 어긋나면 주문 이력이 정합하지 않은 채로 남는다. 두 값 모두 {@link Coupon}에서
 *       계산해 정합을 보장한다</li>
 * </ol>
 *
 * <p>발급 경로({@code KafkaCouponIssueService})는 건드리지 않는다. 선착순 발급 성능이
 * 이 프로젝트의 정체성이고, 그쪽에 Outbox나 추가 조회를 넣으면 벤치마크가 무너진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSagaService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final OrderOutboxHelper orderOutboxHelper;

    @Transactional
    public void handle(CouponRequest request) {
        // ① 참여자의 멱등성 — 이미 처리했으면 도메인을 다시 실행하지 않고 응답을 다시 보낸다
        Optional<OrderOutbox> processed =
                orderOutboxHelper.findProcessed(request.sagaId(), request.couponOrderStatus());
        if (processed.isPresent()) {
            processed.get().markForRepublish();
            log.info("이미 처리된 쿠폰 요청, 기존 응답을 재발행한다: sagaId={}, requestStatus={}",
                    request.sagaId(), request.couponOrderStatus());
            return;
        }

        switch (request.couponOrderStatus()) {
            case PENDING -> apply(request);
            case CANCELLED -> restore(request);
        }
    }

    /** 정상 실행. 쿠폰을 소진하고 할인 금액을 계산해 돌려준다. */
    private void apply(CouponRequest request) {
        IssuedCoupon issued = issuedCouponRepository
                .findByUserIdAndCouponId(request.userId(), request.couponId())
                .orElse(null);

        // 발급 API의 201은 Redis 수량 확보까지만 뜻한다. row는 컨슈머가 나중에 만들므로
        // 발급 직후 주문하면 여기 걸린다. 사용자가 조금 뒤 다시 주문하면 성공하는 일시적 지연이다.
        if (issued == null) {
            log.info("발급 반영 전이라 적용할 쿠폰이 없다: sagaId={}, userId={}, couponId={}",
                    request.sagaId(), request.userId(), request.couponId());
            orderOutboxHelper.saveFailed(request, null, ErrorCode.COUPON_NOT_ISSUED_YET.getCode());
            return;
        }

        if (issued.getStatus() == IssuedCouponStatus.USED) {
            log.info("이미 사용된 쿠폰: sagaId={}, issuedCouponId={}", request.sagaId(), issued.getId());
            orderOutboxHelper.saveFailed(request, issued.getId(), ErrorCode.COUPON_ALREADY_USED.getCode());
            return;
        }

        Coupon coupon = couponRepository.findById(request.couponId()).orElse(null);
        if (coupon == null) {
            log.warn("발급 이력은 있는데 쿠폰이 없다: sagaId={}, couponId={}",
                    request.sagaId(), request.couponId());
            orderOutboxHelper.saveFailed(request, issued.getId(), ErrorCode.COUPON_NOT_FOUND.getCode());
            return;
        }

        // MinOrderAmountDecorator는 미달 시 예외 없이 할인 0을 돌려준다. 그 동작을 그대로 두면
        // 조용히 할인 0으로 주문이 완료되는데 쿠폰은 소진된다 — 사용자는 이유를 알 수 없다.
        if (!coupon.satisfiesMinOrderAmount(request.orderAmount())) {
            log.info("최소 주문 금액 미달: sagaId={}, 필요={}, 주문={}",
                    request.sagaId(), coupon.getMinOrderAmount(), request.orderAmount());
            orderOutboxHelper.saveFailed(request, issued.getId(),
                    ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET.getCode());
            return;
        }

        try {
            issued.use(request.orderId(), request.userId());
        } catch (BusinessException e) {
            // 도메인 가드는 최종 방어선이다. 위 검사들을 통과했으면 여기 오지 않아야 정상이고,
            // 왔다면 그 사이 상태가 바뀐 것이다. 예외를 다시 던지면 응답 Outbox까지 롤백된다.
            log.info("쿠폰 사용이 도메인 가드에 막혔다: sagaId={}, issuedCouponId={}, code={}",
                    request.sagaId(), issued.getId(), e.getErrorCode().getCode());
            orderOutboxHelper.saveFailed(request, issued.getId(), e.getErrorCode().getCode());
            return;
        }

        // 두 값을 같은 곳에서 계산해야 total - discount == final 이 성립한다.
        // discountFor는 0 하한이 적용된 '실효' 할인액이다 — 정책이 계산한 원값이 아니다.
        long discountAmount = coupon.discountFor(request.orderAmount());
        long finalAmount = coupon.finalPrice(request.orderAmount());

        orderOutboxHelper.saveApplied(request, issued.getId(), discountAmount, finalAmount);
        log.info("쿠폰 적용: sagaId={}, orderId={}, issuedCouponId={}, 할인={}, 최종={}",
                request.sagaId(), request.orderId(), issued.getId(), discountAmount, finalAmount);
    }

    /**
     * 보상 실행. 쿠폰 사용을 되돌린다.
     *
     * <p>현재 사가는 이 경로로 요청을 보내지 않는다 — 쿠폰 적용이 마지막 단계라 그 뒤에 실패할
     * 스텝이 없고, 명시적인 적용 실패는 "적용된 적 없음"을 뜻하므로 되돌릴 것이 없다.
     * 적용 결과가 timeout으로 불확실해지거나 뒤에 새 단계가 생기면 그때 쓰인다.
     */
    private void restore(CouponRequest request) {
        IssuedCoupon issued = issuedCouponRepository
                .findByUserIdAndCouponId(request.userId(), request.couponId())
                .orElse(null);

        if (issued == null) {
            // 되돌릴 것이 없으니 성공으로 답한다. 실패로 답하면 조정자가
            // "보상에 실패했다 = 자동 회복 불가"로 판단해버린다.
            log.info("되돌릴 쿠폰이 없어 복구를 건너뛴다: sagaId={}, couponId={}",
                    request.sagaId(), request.couponId());
            orderOutboxHelper.saveRestored(request, null);
            return;
        }

        // restore()는 이미 되돌렸거나 다른 주문이 쓴 쿠폰이면 false를 돌려준다
        if (issued.restore(request.orderId())) {
            log.info("쿠폰 복구: sagaId={}, orderId={}, issuedCouponId={}",
                    request.sagaId(), request.orderId(), issued.getId());
        }

        orderOutboxHelper.saveRestored(request, issued.getId());
    }
}
