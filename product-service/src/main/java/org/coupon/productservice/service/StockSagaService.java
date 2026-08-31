package org.coupon.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.StockRequest;
import org.coupon.common.exception.ErrorCode;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.domain.StockReservation;
import org.coupon.productservice.domain.outbox.OrderOutbox;
import org.coupon.productservice.exception.ProductOutOfStockException;
import org.coupon.productservice.repository.ProductRepository;
import org.coupon.productservice.repository.StockReservationRepository;
import org.coupon.productservice.service.outbox.OrderOutboxHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSagaService {

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final OrderOutboxHelper orderOutboxHelper;

    @Transactional
    public void handle(StockRequest request) {
        Optional<OrderOutbox> processed =
                orderOutboxHelper.findProcessed(request.sagaId(), request.stockOrderStatus());
        if (processed.isPresent()) {
            processed.get().markForRepublish();
            log.info("이미 처리된 재고 요청, 기존 응답을 재발행한다: sagaId={}, requestStatus={}",
                    request.sagaId(), request.stockOrderStatus());
            return;
        }

        switch (request.stockOrderStatus()) {
            case PENDING -> reserve(request);
            case CANCELLED -> restore(request);
        }
    }

    private void reserve(StockRequest request) {
        Product product = productRepository.findById(request.productId()).orElse(null);
        if (product == null) {
            log.warn("존재하지 않는 상품의 재고 예약 요청: sagaId={}, productId={}",
                    request.sagaId(), request.productId());
            orderOutboxHelper.saveFailed(request, ErrorCode.PRODUCT_NOT_FOUND.getCode());
            return;
        }

        try {
            product.decrease(request.quantity());
        } catch (ProductOutOfStockException e) {
            log.info("재고 부족으로 예약 실패: sagaId={}, productId={}, 요청수량={}",
                    request.sagaId(), request.productId(), request.quantity());
            orderOutboxHelper.saveFailed(request, ErrorCode.PRODUCT_OUT_OF_STOCK.getCode());
            return;
        }

        stockReservationRepository.save(StockReservation.builder()
                .orderId(request.orderId())
                .product(product)
                .quantity(request.quantity().longValue())
                .build());

        orderOutboxHelper.saveReserved(request, product.getName(), product.getPrice());
        log.info("재고 예약: sagaId={}, orderId={}, productId={}, 수량={}, 잔여={}",
                request.sagaId(), request.orderId(), product.getId(), request.quantity(), product.getStock());
    }

    private void restore(StockRequest request) {
        StockReservation reservation =
                stockReservationRepository.findByOrderId(request.orderId()).orElse(null);

        if (reservation == null) {
            log.info("되돌릴 예약이 없어 복구를 건너뛴다: sagaId={}, orderId={}",
                    request.sagaId(), request.orderId());
            orderOutboxHelper.saveRestored(request);
            return;
        }

        if (reservation.restore()) {
            reservation.getProduct().restore(reservation.getQuantity());
            log.info("재고 복구: sagaId={}, orderId={}, productId={}, 수량={}",
                    request.sagaId(), request.orderId(),
                    reservation.getProduct().getId(), reservation.getQuantity());
        }

        orderOutboxHelper.saveRestored(request);
    }
}
