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

/**
 * 재고 요청을 처리하고 결과를 응답 Outbox에 남긴다. 사가에서 product-service가 맡은 전부다.
 *
 * <p>세 가지 규율이 이 클래스를 지탱한다.
 * <ol>
 *   <li><b>재고 변경과 응답 기록이 하나의 트랜잭션</b> — 갈라지면 "재고는 깎였는데 응답은 없는"
 *       상태가 생기고 사가가 영영 멈춘다</li>
 *   <li><b>비즈니스 실패를 예외로 흘리지 않는다</b> — 재고 부족은 사가의 정상 결과다.
 *       예외를 통과시키면 트랜잭션이 롤백되며 응답 row까지 사라진다</li>
 *   <li><b>중복 요청에는 기존 응답을 다시 보낸다</b> — 조정자처럼 조용히 return하면 안 된다.
 *       중복이 왔다는 건 앞선 응답이 도달하지 못했다는 뜻이기 때문이다</li>
 * </ol>
 *
 * <p>재고에 DB 락을 걸지 않는 것은 {@code productId}를 파티션 키로 삼아 같은 상품의 요청이
 * 한 파티션에서 직렬화된다는 전제 덕분이다. 이 클래스가 그 전제를 지키는 유일한 재고 변경 경로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSagaService {

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final OrderOutboxHelper orderOutboxHelper;

    @Transactional
    public void handle(StockRequest request) {
        // ① 참여자의 멱등성 — 이미 처리했으면 도메인을 다시 실행하지 않고 응답을 다시 보낸다
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

    /** 정상 실행. 재고를 예약하고 주문이 총액을 확정할 수 있도록 단가 스냅샷을 실어 보낸다. */
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
            // 여기서 다시 던지면 트랜잭션이 롤백되어 응답 Outbox까지 사라진다.
            // 재고 부족은 "처리에 실패한 것"이 아니라 "처리해 보니 안 된다는 결과"다.
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

    /** 보상 실행. 예약을 되돌린다. */
    private void restore(StockRequest request) {
        StockReservation reservation =
                stockReservationRepository.findByOrderId(request.orderId()).orElse(null);

        if (reservation == null) {
            // 예약이 없다 = 예약 자체가 실패했던 사가다. 되돌릴 것이 없으니 성공으로 답한다.
            // 실패로 답하면 조정자가 "보상에 실패했다"고 판단해 회복 불가 상태로 본다.
            log.info("되돌릴 예약이 없어 복구를 건너뛴다: sagaId={}, orderId={}",
                    request.sagaId(), request.orderId());
            orderOutboxHelper.saveRestored(request);
            return;
        }

        // restore()는 이미 되돌린 예약이면 false를 돌려준다 — 재고를 두 번 더하지 않기 위한 가드다
        if (reservation.restore()) {
            reservation.getProduct().restore(reservation.getQuantity());
            log.info("재고 복구: sagaId={}, orderId={}, productId={}, 수량={}",
                    request.sagaId(), request.orderId(),
                    reservation.getProduct().getId(), reservation.getQuantity());
        }

        orderOutboxHelper.saveRestored(request);
    }
}
