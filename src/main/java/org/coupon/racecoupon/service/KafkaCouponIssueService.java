package org.coupon.racecoupon.service;

import lombok.RequiredArgsConstructor;
import org.coupon.racecoupon.domain.Coupon;
import org.coupon.racecoupon.dto.CouponIssueResponse;
import org.coupon.racecoupon.exception.CouponNotFoundException;
import org.coupon.racecoupon.exception.CouponSoldOutException;
import org.coupon.racecoupon.kafka.CouponIssueMessage;
import org.coupon.racecoupon.kafka.CouponIssueProducer;
import org.coupon.racecoupon.repository.CouponCountRedisRepository;
import org.coupon.racecoupon.repository.CouponRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaCouponIssueService {

    private final CouponRepository couponRepository;
    private final CouponCountRedisRepository couponCountRedisRepository;
    private final CouponIssueProducer couponIssueProducer;

    public CouponIssueResponse issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        long totalQuantity = coupon.getTotalQuantity();

        Long count = couponCountRedisRepository.increment(couponId);
        if (count == null || count > totalQuantity) {
            couponCountRedisRepository.decrement(couponId);
            throw new CouponSoldOutException();
        }

        couponIssueProducer.issue(new CouponIssueMessage(couponId, userId));

        return new CouponIssueResponse(couponId, count, totalQuantity - count);
    }
}
