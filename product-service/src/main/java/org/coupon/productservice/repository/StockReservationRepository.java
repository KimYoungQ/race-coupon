package org.coupon.productservice.repository;

import org.coupon.productservice.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    /**
     * 보상 처리에서 되돌릴 예약을 찾는다.
     *
     * <p>{@code UNIQUE(order_id)} 덕분에 결과가 많아야 1건이다. 그 제약은 FK가 아니라
     * 멱등 키라, 같은 예약 요청을 두 번 받아도 재고가 두 번 깎이지 않게 막아준다.
     */
    Optional<StockReservation> findByOrderId(Long orderId);
}
