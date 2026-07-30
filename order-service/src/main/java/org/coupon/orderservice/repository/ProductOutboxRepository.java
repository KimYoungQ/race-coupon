package org.coupon.orderservice.repository;

import org.coupon.common.event.StockOrderStatus;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.common.outbox.SagaStatus;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductOutboxRepository extends JpaRepository<ProductOutbox, UUID> {

    /**
     * 멱등성 1차 방어선. 지금 이 응답을 받아들일 수 있는 단계인지 확인한다.
     *
     * <p>{@code requestStatus}가 조건에 반드시 들어가야 한다. 정상 요청과 보상 요청이
     * 같은 sagaId를 공유하므로 빼면 서로를 같은 요청으로 오인한다.
     *
     * <p>결과가 비어 있으면 이미 처리됐거나 늦게 도착한 응답이다. 예외를 던지지 않고 조용히 무시한다.
     */
    Optional<ProductOutbox> findByTypeAndSagaIdAndRequestStatusAndSagaStatusIn(
            String type, UUID sagaId, StockOrderStatus requestStatus, List<SagaStatus> sagaStatuses);

    /**
     * 상태를 가리지 않고 찾는다. 사가가 끝날 때 이미 전이된 row의 상태를 맞추는 용도다.
     *
     * <p>쿠폰 응답으로 사가가 종료돼도 재고 Outbox는 {@code PROCESSING}에 멈춰 있다.
     * 여기서 {@code SUCCEEDED}로 올려주지 않으면 Cleaner가 영원히 지우지 못한다.
     */
    Optional<ProductOutbox> findByTypeAndSagaIdAndRequestStatus(
            String type, UUID sagaId, StockOrderStatus requestStatus);

    /**
     * 스케줄러 폴링(발행 대상)과 Cleaner(정리 대상) 양쪽이 쓴다. 무엇을 찾을지는 인자가 정한다.
     *
     * <p>발행 대상으로 쓸 때 <b>{@code sagaStatuses}에 COMPENSATING을 반드시 포함해야 한다.</b>
     * 보상 요청도 Outbox를 거쳐 나가기 때문이다. 빼면 정방향은 멀쩡히 도는데 보상만 영원히
     * 발행되지 않고, 정상 시나리오 테스트로는 절대 발견되지 않는다.
     */
    List<ProductOutbox> findByTypeAndOutboxStatusAndSagaStatusIn(
            String type, OutboxStatus outboxStatus, List<SagaStatus> sagaStatuses);
}
