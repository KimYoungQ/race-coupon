package org.coupon.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockResponse;
import org.coupon.orderservice.exception.InvalidOrderStateException;
import org.coupon.orderservice.saga.OrderProductSaga;
import org.coupon.orderservice.saga.SagaTraceTag;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductResponseListener {

    private final OrderProductSaga orderProductSaga;
    private final SagaTraceTag sagaTraceTag;

    @KafkaListener(
            id = "order-product-saga",
            groupId = "order-saga",
            topics = SagaTopics.PRODUCT_RESPONSE,
            containerFactory = "sagaListenerContainerFactory")
    public void receive(List<StockResponse> responses) {
        for (int index = 0; index < responses.size(); index++) {
            StockResponse response = responses.get(index);

            try (var ignored = sagaTraceTag.open(SagaTraceTag.SAGA_CONSUME, response.sagaId())) {
                dispatch(response);
            } catch (OptimisticLockingFailureException e) {
                log.info("동시 중복 응답 무시: sagaId={}", response.sagaId());
            } catch (InvalidOrderStateException e) {
                log.info("지연/무효 응답 무시: sagaId={}, stockStatus={}",
                        response.sagaId(), response.stockStatus());
            } catch (Exception e) {
                throw new BatchListenerFailedException("재고 응답 처리 실패", e, index);
            }
        }
    }

    private void dispatch(StockResponse response) {
        switch (response.stockStatus()) {
            case RESERVED -> orderProductSaga.stockReserved(response);
            case RESTORED -> orderProductSaga.stockRestored(response);
            case FAILED -> orderProductSaga.stockFailed(response);
        }
    }
}
