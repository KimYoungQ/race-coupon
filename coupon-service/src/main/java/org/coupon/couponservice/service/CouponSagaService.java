package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.exception.ErrorCode;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSagaService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponApplyResponsePayload apply(CouponApplyRequestPayload request) {
        IssuedCouponStatus status = issuedCouponRepository.findStatusBy(request.userId(), request.couponId());

        if (status == null) {
            log.info("쿠폰 발급 정보가 아직 반영되지 않음: orderId={}, userId={}, couponId={}",
                    request.orderId(), request.userId(), request.couponId());
            return rejected(request, ErrorCode.COUPON_NOT_ISSUED_YET);
        }

        if (status == IssuedCouponStatus.USED) {
            log.info("이미 사용된 쿠폰: orderId={}, userId={}, couponId={}",
                    request.orderId(), request.userId(), request.couponId());
            return rejected(request, ErrorCode.COUPON_ALREADY_USED);
        }

        Coupon coupon = couponRepository.findById(request.couponId()).orElse(null);
        if (coupon == null) {
            log.warn("쿠폰 발급 이력은 존재하지만 쿠폰을 찾을 수 없음: orderId={}, couponId={}", request.orderId(), request.couponId());
            return rejected(request, ErrorCode.COUPON_NOT_FOUND);
        }

        if (!coupon.satisfiesMinOrderAmount(request.orderAmount())) {
            log.info("최소 주문 금액 미달: orderId={}, 필요={}, 주문={}",
                    request.orderId(), coupon.getMinOrderAmount(), request.orderAmount());
            return rejected(request, ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET);
        }

        long used = issuedCouponRepository.markUsed(
                request.userId(), request.couponId(), request.orderId(), LocalDateTime.now());
        if (used == 0) {
            log.info("쿠폰 사용 처리 실패 - 다른 요청에서 먼저 사용함: orderId={}, userId={}, couponId={}",
                    request.orderId(), request.userId(), request.couponId());
            return rejected(request, ErrorCode.COUPON_ALREADY_USED);
        }

        long orderAmount = request.orderAmount();
        long finalAmount = coupon.finalPrice(orderAmount);
        long discountAmount = orderAmount - finalAmount;

        log.info("쿠폰 적용 완료: orderId={}, userId={}, couponId={}, 할인={}, 최종={}",
                request.orderId(), request.userId(), request.couponId(), discountAmount, finalAmount);
        return new CouponApplyResponsePayload(
                request.orderId(), discountAmount, finalAmount, CouponResult.APPLIED, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponApplyResponsePayload cancel(CouponApplyRequestPayload request) {
        long restored = issuedCouponRepository.restore(
                request.userId(), request.couponId(), request.orderId());

        if (restored == 0) {
            log.info("쿠폰 복구 대상이 아님: orderId={}, userId={}, couponId={}",
                    request.orderId(), request.userId(), request.couponId());
        } else {
            log.info("쿠폰 복구 완료: orderId={}, userId={}, couponId={}",
                    request.orderId(), request.userId(), request.couponId());
        }

        return new CouponApplyResponsePayload(request.orderId(), null, null, CouponResult.CANCELLED, null);
    }

    private CouponApplyResponsePayload rejected(CouponApplyRequestPayload request, ErrorCode code) {
        return new CouponApplyResponsePayload(
                request.orderId(), null, null, CouponResult.REJECTED, code.getCode());
    }
}
