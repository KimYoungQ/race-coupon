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
