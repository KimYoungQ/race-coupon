package org.coupon.couponservice.repository;

import org.coupon.couponservice.domain.Coupon;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepositoryCustom {

    /**
     * 발급 수량을 1 증가시킨다. 발급 컨슈머가 {@code IssuedCoupon} 저장과 같은 트랜잭션에서 부른다.
     *
     * <p>{@code Coupon#issue()}를 쓰지 않는 이유가 둘이다. 하나는 그 메서드가 read → +1 → write라
     * 락 없이는 안전하지 않다는 것이고, 다른 하나는 재고 초과 시 예외를 던진다는 것이다.
     * 발급 리스너에는 DLT가 없으므로 컨슈머 안에서 던져진 예외는 영원히 재시도되는 메시지가 된다.
     * 여기서는 조건 없이 DB 내부 원자 연산으로 올린다 — 수량 판정은 이미 Redis가 끝냈다.
     *
     * @return 갱신된 row 수. 쿠폰이 없으면 0
     */
    long increaseIssuedQuantity(Long couponId);

    /**
     * {@code userId}가 지금 발급받을 수 있는 쿠폰. 이벤트가 진행 중이고, 재고가 남았고,
     * 본인이 아직 받지 않은 것만 나온다.
     *
     * <p>재고는 {@code Coupon.issuedQuantity}로 판정한다. 컨슈머 랙만큼 뒤처지므로
     * 방금 발급된 건이 잠깐 반영되지 않는다 — 그 사이 발급을 시도하면 409가 나는데,
     * 목록은 스냅샷이고 최종 방어는 발급 API가 한다.
     */
    List<Coupon> findIssuableBy(Long userId, LocalDateTime now);

    /*
    Optional<Coupon> findWithPessimisticLockById(Long id);
    */
}
