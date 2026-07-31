package org.coupon.couponservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.dto.CouponIssueAcceptedResponse;
import org.coupon.couponservice.dto.CouponIssueResponse;
import org.coupon.couponservice.exception.CouponAlreadyIssuedException;
import org.coupon.couponservice.exception.CouponEventEndedException;
import org.coupon.couponservice.exception.CouponNotFoundException;
import org.coupon.couponservice.exception.CouponSoldOutException;
import org.coupon.couponservice.kafka.CouponIssueMessage;
import org.coupon.couponservice.kafka.CouponIssueProducer;
import org.coupon.couponservice.mapper.CouponMapper;
import org.coupon.couponservice.repository.CouponIssueRedisRepository;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 선착순 발급 V4 — Redis Lua로 자격을 확정하고 영속화는 Kafka에 위임한다.
 *
 * <p>중복 검증·수량 검증·카운터 증가·TTL 설정이 {@code issue_coupon.lua} 한 덩어리 안에서
 * 원자적으로 끝난다. 검증을 통과한 요청만 카운터를 올리므로 <b>카운터가 곧 발급 수</b>이고,
 * 그래서 응답의 {@code remaining}이 음수가 되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaCouponIssueService {

    private static final long ALREADY_ISSUED = 0L;
    private static final long SOLD_OUT = -1L;

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponIssueRedisRepository couponIssueRedisRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final CouponMapper couponMapper;

    public CouponIssueAcceptedResponse issue(Long couponId, Long userId) {
        Coupon coupon = getCoupon(couponId);

        long ttlSeconds = issueTtlSeconds(coupon);
        if (ttlSeconds <= 0) {
            throw new CouponEventEndedException(couponId);
        }

        long result = couponIssueRedisRepository.issue(
                couponId, userId, coupon.getTotalQuantity(), ttlSeconds);

        if (result > 0) {
            couponIssueProducer.issue(new CouponIssueMessage(couponId, userId));
            return new CouponIssueAcceptedResponse(couponId, userId);
        }
        if (result == ALREADY_ISSUED) {
            return reissueOrReject(couponId, userId);
        }
        if (result == SOLD_OUT) {
            throw new CouponSoldOutException();
        }

        // 이벤트 종료는 위에서 이미 걸러냈다. 여기까지 왔다면 이 인스턴스의 시계가 어긋난 것이다.
        log.warn("발급 스크립트가 TTL 무효를 반환했다. 인스턴스 시계 확인 필요: couponId={}, ttlSeconds={}",
                couponId, ttlSeconds);
        throw new CouponEventEndedException(couponId);
    }

    /**
     * 발급 메시지를 소비해 실제 발급 건을 남긴다. 컨슈머가 트랜잭션 밖에서 호출한다.
     *
     * <p>두 쓰기가 한 트랜잭션이어야 한다. 중복 메시지면 UNIQUE 위반으로 통째로 롤백되어
     * row도 안 생기고 수량도 안 오른다. 컨슈머가 이 메서드 <b>밖에서</b> 예외를 잡는 것이 중요하다 —
     * 같은 메서드 안에서 잡으면 이미 롤백 표시된 트랜잭션을 이어 갈 수 없다.
     */
    @Transactional
    public void persist(Long couponId, Long userId) {
        issuedCouponRepository.save(IssuedCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .build());
        couponRepository.increaseIssuedQuantity(couponId);
    }

    @Transactional(readOnly = true)
    public CouponIssueResponse getCouponInfo(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        long issued = issuedCouponRepository.countByCouponId(couponId);

        return couponMapper.toIssueResponse(coupon, issued);
    }

    /**
     * Redis 발급자 집합에는 있지만 발급 건이 없을 수 있다 — 메시지 발행이 실패했거나 컨슈머가
     * 아직 따라오지 못한 경우다. 여기서 무조건 409를 주면 발행이 실패했던 사용자는 재시도할 길이
     * 막혀 발급이 영구 유실된다. 그래서 row를 확인하고, 없으면 재발행한다.
     * 컨슈머가 {@code UNIQUE(user_id, coupon_id)}로 멱등하므로 중복 발행은 무해하다.
     */
    private CouponIssueAcceptedResponse reissueOrReject(Long couponId, Long userId) {
        if (issuedCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
            throw new CouponAlreadyIssuedException(userId, couponId);
        }

        log.warn("발급 이력은 있으나 발급 건이 없어 재발행한다: couponId={}, userId={}", couponId, userId);
        couponIssueProducer.issue(new CouponIssueMessage(couponId, userId));

        return new CouponIssueAcceptedResponse(couponId, userId);
    }

    /**
     * 이벤트 종료 시각에서 역산한다. 종료 시각이 지나면 발급 자체를 거절하므로,
     * 두 키가 그 시점에 함께 사라지는 것으로 충분하다.
     */
    private long issueTtlSeconds(Coupon coupon) {
        return Duration.between(LocalDateTime.now(), coupon.getEventEndAt()).toSeconds();
    }

    private Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
    }
}
