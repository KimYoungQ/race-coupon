package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.exception.CouponNotFoundException;
import org.coupon.couponservice.exception.CouponSoldOutException;
import org.coupon.couponservice.kafka.CouponIssueMessage;
import org.coupon.couponservice.kafka.CouponIssueProducer;
import org.coupon.couponservice.repository.CouponCountRedisRepository;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KafkaCouponIssueService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponCountRedisRepository couponCountRedisRepository;
    private final CouponIssueProducer couponIssueProducer;

    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = getCoupon(couponId);
        long totalQuantity = coupon.getTotalQuantity();

        Long count = couponCountRedisRepository.increment(couponId);
        if (count == null || count > totalQuantity) {
            couponCountRedisRepository.decrement(couponId);
            throw new CouponSoldOutException();
        }

        couponIssueProducer.issue(new CouponIssueMessage(couponId, userId));

        return new CouponIssueResponse(couponId, count, totalQuantity - count);
    }

    @Transactional(readOnly = true)
    public CouponIssueResponse getCouponInfo(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        long totalQuantity = coupon.getTotalQuantity();
        long issued = issuedCouponRepository.countByCouponId(couponId);

        return new CouponIssueResponse(couponId, issued, totalQuantity - issued);
    }

    private Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
    }
}
