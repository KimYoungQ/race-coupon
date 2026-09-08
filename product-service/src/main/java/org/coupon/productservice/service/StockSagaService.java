package org.coupon.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.StockReservationRequestPayload;
import org.coupon.common.event.StockReservationResponsePayload;
import org.coupon.common.event.StockResult;
import org.coupon.common.exception.ErrorCode;
import org.coupon.productservice.domain.Product;
import org.coupon.productservice.domain.ReservationStatus;
import org.coupon.productservice.domain.StockReservation;
import org.coupon.productservice.exception.ProductNotFoundException;
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
        long quantity = request.quantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "재고 예약 수량이 올바르지 않습니다: orderId=" + request.orderId() + ", 수량=" + quantity);
        }

        StockReservation existing = stockReservationRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            return respondToDuplicate(request, existing);
        }

        Product product = productRepository.findById(request.productId()).orElse(null);
        if (product == null) {
            log.warn("존재하지 않는 상품의 재고 예약 요청: orderId={}, productId={}",
                    request.orderId(), request.productId());
            return rejected(request, ErrorCode.PRODUCT_NOT_FOUND);
        }

        StockReservation reservation = stockReservationRepository.saveAndFlush(StockReservation.builder()
                .orderId(request.orderId())
                .product(product)
                .quantity(quantity)
                .build());

        if (productRepository.decreaseStock(request.productId(), quantity) == 0) {
            stockReservationRepository.delete(reservation);
            log.info("재고 부족으로 예약 실패: orderId={}, productId={}, 요청수량={}",
                    request.orderId(), request.productId(), quantity);
            return rejected(request, ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        log.info("재고 예약 완료: orderId={}, productId={}, 수량={}",
                request.orderId(), request.productId(), quantity);
        return reserved(request, product);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockReservationResponsePayload release(StockReservationRequestPayload request) {
        StockReservation reservation =
                stockReservationRepository.findByOrderId(request.orderId()).orElse(null);

        if (reservation == null) {
            log.info("재고 예약 정보를 찾을 수 없어 복구하지 않음: orderId={}", request.orderId());
            return released(request);
        }

        Long productId = reservation.getProduct().getId();
        long quantity = reservation.getQuantity();

        if (stockReservationRepository.markRestored(request.orderId()) == 0) {
            log.info("이미 복구된 재고 예약: orderId={}", request.orderId());
            return released(request);
        }

        if (productRepository.increaseStock(productId, quantity) == 0) {
            throw new ProductNotFoundException(productId);
        }

        log.info("재고 복구 완료: orderId={}, productId={}, 수량={}", request.orderId(), productId, quantity);
        return released(request);
    }

    private StockReservationResponsePayload respondToDuplicate(
            StockReservationRequestPayload request, StockReservation reservation) {
        if (reservation.getStatus() == ReservationStatus.RESTORED) {
            log.info("이미 복구된 예약에 대한 재고 예약 재요청: orderId={}", request.orderId());
            return rejected(request, ErrorCode.INVALID_ORDER_STATE);
        }

        Product product = reservation.getProduct();
        log.info("이미 예약된 주문의 재고 예약 재요청: orderId={}, productId={}",
                request.orderId(), product.getId());
        return reserved(request, product);
    }

    private StockReservationResponsePayload reserved(StockReservationRequestPayload request, Product product) {
        return new StockReservationResponsePayload(
                request.orderId(), product.getName(), product.getPrice(), StockResult.RESERVED, null);
    }

    private StockReservationResponsePayload released(StockReservationRequestPayload request) {
        return new StockReservationResponsePayload(
                request.orderId(), null, null, StockResult.RELEASED, null);
    }

    private StockReservationResponsePayload rejected(StockReservationRequestPayload request, ErrorCode code) {
        return new StockReservationResponsePayload(
                request.orderId(), null, null, StockResult.OUT_OF_STOCK, code.getCode());
    }
}
