package org.coupon.productservice.repository;

import org.coupon.productservice.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
}
