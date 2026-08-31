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

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSagaService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final OrderOutboxHelper orderOutboxHelper;

    @Transactional
    public void handle(CouponRequest request) {
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

    private void apply(CouponRequest request) {
        IssuedCoupon issued = issuedCouponRepository
                .findByUserIdAndCouponId(request.userId(), request.couponId())
                .orElse(null);

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
            log.info("쿠폰 사용이 도메인 가드에 막혔다: sagaId={}, issuedCouponId={}, code={}",
                    request.sagaId(), issued.getId(), e.getErrorCode().getCode());
            orderOutboxHelper.saveFailed(request, issued.getId(), e.getErrorCode().getCode());
            return;
        }

        long discountAmount = coupon.discountFor(request.orderAmount());
        long finalAmount = coupon.finalPrice(request.orderAmount());

        orderOutboxHelper.saveApplied(request, issued.getId(), discountAmount, finalAmount);
        log.info("쿠폰 적용: sagaId={}, orderId={}, issuedCouponId={}, 할인={}, 최종={}",
                request.sagaId(), request.orderId(), issued.getId(), discountAmount, finalAmount);
    }

    private void restore(CouponRequest request) {
        IssuedCoupon issued = issuedCouponRepository
                .findByUserIdAndCouponId(request.userId(), request.couponId())
                .orElse(null);

        if (issued == null) {
            log.info("되돌릴 쿠폰이 없어 복구를 건너뛴다: sagaId={}, couponId={}",
                    request.sagaId(), request.couponId());
            orderOutboxHelper.saveRestored(request, null);
            return;
        }

        if (issued.restore(request.orderId())) {
            log.info("쿠폰 복구: sagaId={}, orderId={}, issuedCouponId={}",
                    request.sagaId(), request.orderId(), issued.getId());
        }

        orderOutboxHelper.saveRestored(request, issued.getId());
    }
}
