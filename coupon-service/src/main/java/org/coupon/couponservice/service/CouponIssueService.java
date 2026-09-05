package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.dto.CouponIssueAcceptedResponse;
import org.coupon.couponservice.dto.CouponStockResponse;
import org.coupon.couponservice.exception.CouponAlreadyIssuedException;
import org.coupon.couponservice.exception.CouponEventEndedException;
import org.coupon.couponservice.exception.CouponNotFoundException;
import org.coupon.couponservice.exception.CouponSoldOutException;
import org.coupon.couponservice.kafka.CouponIssueMessage;
import org.coupon.couponservice.kafka.CouponIssueProducer;
import org.coupon.couponservice.mapper.CouponMapper;
import org.coupon.couponservice.metrics.CouponIssueMetrics;
import org.coupon.couponservice.repository.CouponIssueRedisRepository;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private static final long ALREADY_ISSUED = 0L;
    private static final long SOLD_OUT = -1L;

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponIssueRedisRepository couponIssueRedisRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final CouponMapper couponMapper;
    private final CouponIssueMetrics metrics;

    public CouponIssueAcceptedResponse issue(Long couponId, Long userId) {
        return metrics.recordDecision(() -> doIssue(couponId, userId));
    }

    private CouponIssueAcceptedResponse doIssue(Long couponId, Long userId) {
        Coupon coupon = getCoupon(couponId);

        long ttlSeconds = issueTtlSeconds(coupon);
        if (ttlSeconds <= 0) {
            metrics.rejectedEventEnded();
            throw new CouponEventEndedException(couponId);
        }

        long result = couponIssueRedisRepository.issue(
                couponId, userId, coupon.getTotalQuantity(), ttlSeconds);

        if (result > 0) {
            metrics.accepted();
            couponIssueProducer.issue(new CouponIssueMessage(couponId, userId));
            return new CouponIssueAcceptedResponse(couponId, userId);
        }
        if (result == ALREADY_ISSUED) {
            return reissueOrReject(couponId, userId);
        }
        if (result == SOLD_OUT) {
            metrics.rejectedSoldOut();
            throw new CouponSoldOutException();
        }

        log.warn("발급 스크립트가 TTL 무효를 반환했다. 인스턴스 시계 확인 필요: couponId={}, ttlSeconds={}",
                couponId, ttlSeconds);
        metrics.rejectedEventEnded();
        throw new CouponEventEndedException(couponId);
    }

    @Transactional
    public void persist(Long couponId, Long userId) {
        issuedCouponRepository.save(IssuedCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .build());
        couponRepository.increaseIssuedQuantity(couponId);
    }

    @Transactional(readOnly = true)
    public CouponStockResponse getCouponInfo(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        long issued = issuedCouponRepository.countByCouponId(couponId);

        return couponMapper.toStockResponse(coupon, issued);
    }

    private CouponIssueAcceptedResponse reissueOrReject(Long couponId, Long userId) {
        if (issuedCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
            metrics.rejectedAlreadyIssued();
            throw new CouponAlreadyIssuedException(userId, couponId);
        }

        log.warn("발급 이력은 있으나 발급 건이 없어 재발행한다: couponId={}, userId={}", couponId, userId);
        couponIssueProducer.issue(new CouponIssueMessage(couponId, userId));

        return new CouponIssueAcceptedResponse(couponId, userId);
    }

    private long issueTtlSeconds(Coupon coupon) {
        return Duration.between(LocalDateTime.now(), coupon.getEventEndAt()).toSeconds();
    }

    private Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
    }
}
