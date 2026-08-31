package org.coupon.productservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockRequest;
import org.coupon.productservice.saga.SagaTraceTag;
import org.coupon.productservice.service.StockSagaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRequestListener {

    private final StockSagaService stockSagaService;
    private final SagaTraceTag sagaTraceTag;

    @KafkaListener(
            id = "product-saga",
            groupId = "product-saga",
            topics = SagaTopics.PRODUCT_REQUEST,
            containerFactory = "sagaListenerContainerFactory")
    public void receive(List<StockRequest> requests) {
        for (int index = 0; index < requests.size(); index++) {
            StockRequest request = requests.get(index);

            try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, request.sagaId())) {
                stockSagaService.handle(request);
            } catch (DataIntegrityViolationException e) {
                log.info("동시 중복 요청, 다른 스레드가 이미 처리했다: sagaId={}, requestStatus={}",
                        request.sagaId(), request.stockOrderStatus());
            } catch (OptimisticLockingFailureException e) {
                log.info("동시 처리 감지, 이 스레드는 물러난다: sagaId={}", request.sagaId());
            } catch (Exception e) {
                throw new BatchListenerFailedException("재고 요청 처리 실패", e, index);
            }
        }
    }
}
