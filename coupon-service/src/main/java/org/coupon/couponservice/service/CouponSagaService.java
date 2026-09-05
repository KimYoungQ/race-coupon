package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.exception.BusinessException;
import org.coupon.common.exception.ErrorCode;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSagaService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponApplyResponsePayload apply(CouponApplyRequestPayload request) {
        IssuedCoupon issued = issuedCouponRepository
                .findByUserIdAndCouponId(request.userId(), request.couponId())
                .orElse(null);

        if (issued == null) {
            log.info("발급 반영 전이라 적용할 쿠폰이 없다: orderId={}, userId={}, couponId={}",
                    request.orderId(), request.userId(), request.couponId());
            return rejected(request, ErrorCode.COUPON_NOT_ISSUED_YET);
        }

        if (issued.getStatus() == IssuedCouponStatus.USED) {
            log.info("이미 사용된 쿠폰: orderId={}, issuedCouponId={}", request.orderId(), issued.getId());
            return rejected(request, ErrorCode.COUPON_ALREADY_USED);
        }

        Coupon coupon = couponRepository.findById(request.couponId()).orElse(null);
        if (coupon == null) {
            log.warn("발급 이력은 있는데 쿠폰이 없다: orderId={}, couponId={}", request.orderId(), request.couponId());
            return rejected(request, ErrorCode.COUPON_NOT_FOUND);
        }

        if (!coupon.satisfiesMinOrderAmount(request.orderAmount())) {
            log.info("최소 주문 금액 미달: orderId={}, 필요={}, 주문={}",
                    request.orderId(), coupon.getMinOrderAmount(), request.orderAmount());
            return rejected(request, ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET);
        }

        try {
            issued.use(request.orderId(), request.userId());
        } catch (BusinessException e) {
            log.info("쿠폰 적용 실패: orderId={}, issuedCouponId={}, code={}",
                    request.orderId(), issued.getId(), e.getErrorCode().getCode());
            return rejected(request, e.getErrorCode());
        }

        long orderAmount = request.orderAmount();
        long finalAmount = coupon.finalPrice(orderAmount);
        long discountAmount = orderAmount - finalAmount;

        log.info("쿠폰 적용: orderId={}, issuedCouponId={}, 할인={}, 최종={}",
                request.orderId(), issued.getId(), discountAmount, finalAmount);
        return new CouponApplyResponsePayload(
                request.orderId(), discountAmount, finalAmount, CouponResult.APPLIED, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponApplyResponsePayload cancel(CouponApplyRequestPayload request) {
        IssuedCoupon issued = issuedCouponRepository
                .findByUserIdAndCouponId(request.userId(), request.couponId())
                .orElse(null);

        if (issued == null) {
            log.info("되돌릴 쿠폰이 없어 복구를 건너뛴다: orderId={}, couponId={}",
                    request.orderId(), request.couponId());
        } else if (issued.restore(request.orderId())) {
            log.info("쿠폰 복구: orderId={}, issuedCouponId={}", request.orderId(), issued.getId());
        } else {
            log.info("쿠폰 복구 생략, 사용 상태가 아니거나 주문 ID가 일치하지 않음: orderId={}, issuedCouponId={}, status={}",
                    request.orderId(), issued.getId(), issued.getStatus());
        }

        return new CouponApplyResponsePayload(request.orderId(), null, null, CouponResult.CANCELLED, null);
    }

    private CouponApplyResponsePayload rejected(CouponApplyRequestPayload request, ErrorCode code) {
        return new CouponApplyResponsePayload(
                request.orderId(), null, null, CouponResult.REJECTED, code.getCode());
    }
}
