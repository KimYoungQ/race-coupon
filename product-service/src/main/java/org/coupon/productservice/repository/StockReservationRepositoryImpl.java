package org.coupon.productservice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.coupon.productservice.domain.ReservationStatus;

import static org.coupon.productservice.domain.QStockReservation.stockReservation;

@RequiredArgsConstructor
public class StockReservationRepositoryImpl implements StockReservationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public long markRestored(Long orderId) {
        return queryFactory
                .update(stockReservation)
                .set(stockReservation.status, ReservationStatus.RESTORED)
                .where(
                        stockReservation.orderId.eq(orderId),
                        stockReservation.status.eq(ReservationStatus.RESERVED))
                .execute();
    }
}
