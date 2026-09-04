package org.coupon.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.common.exception.ErrorCode;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.domain.StockReservation;
import org.coupon.productservice.exception.ProductOutOfStockException;
import org.coupon.productservice.repository.ProductRepository;
import org.coupon.productservice.repository.StockReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSagaService {

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public StockReservationResponsePayload reserve(StockReservationRequestPayload request) {
        Product product = productRepository.findById(request.productId()).orElse(null);
        if (product == null) {
            log.warn("존재하지 않는 상품의 재고 예약 요청: orderId={}, productId={}",
                    request.orderId(), request.productId());
            return rejected(request, ErrorCode.PRODUCT_NOT_FOUND);
        }

        try {
            product.decrease(request.quantity());
        } catch (ProductOutOfStockException e) {
            log.info("재고 부족으로 예약 실패: orderId={}, productId={}, 요청수량={}, 잔여={}",
                    request.orderId(), request.productId(), request.quantity(), product.getStock());
            return rejected(request, ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        stockReservationRepository.save(StockReservation.builder()
                .orderId(request.orderId())
                .product(product)
                .quantity(request.quantity().longValue())
                .build());

        log.info("재고 예약: orderId={}, productId={}, 수량={}, 잔여={}",
                request.orderId(), product.getId(), request.quantity(), product.getStock());
        return new StockReservationResponsePayload(
                request.orderId(), product.getName(), product.getPrice(), StockResult.RESERVED, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockReservationResponsePayload release(StockReservationRequestPayload request) {
        StockReservation reservation =
                stockReservationRepository.findByOrderId(request.orderId()).orElse(null);

        if (reservation == null) {
            log.info("되돌릴 예약이 없어 복구를 건너뛴다: orderId={}", request.orderId());
        } else if (reservation.restore()) {
            reservation.getProduct().restore(reservation.getQuantity());
            log.info("재고 복구: orderId={}, productId={}, 수량={}",
                    request.orderId(), reservation.getProduct().getId(), reservation.getQuantity());
        } else {
            log.info("이미 복구된 예약: orderId={}", request.orderId());
        }

        return new StockReservationResponsePayload(request.orderId(), null, null, StockResult.RELEASED, null);
    }

    private StockReservationResponsePayload rejected(StockReservationRequestPayload request, ErrorCode code) {
        return new StockReservationResponsePayload(
                request.orderId(), null, null, StockResult.OUT_OF_STOCK, code.getCode());
    }
}
