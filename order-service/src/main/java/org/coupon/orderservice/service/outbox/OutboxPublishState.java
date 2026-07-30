package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.repository.CouponOutboxRepository;
import org.coupon.orderservice.repository.ProductOutboxRepository;
import org.coupon.orderservice.saga.SagaChannel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 발행 결과를 Outbox row에 반영한다.
 *
 * <p>별도 빈으로 분리한 이유는 두 가지다.
 * <ul>
 *   <li>Kafka 발행 콜백은 <b>다른 스레드</b>에서 트랜잭션 없이 실행된다. 여기서 새 트랜잭션을 열어야 한다</li>
 *   <li>같은 클래스 안에서 호출하면 프록시를 거치지 않아 {@code @Transactional}이 통째로 무시된다</li>
 * </ul>
 *
 * <p>발행 시도 기록({@link #recordAttempt})은 <b>발행 전에</b> 커밋해 둔다. 프로세스가
 * 발행 도중 죽어도 "몇 번 시도했는지"가 남아야 무한 재시도와 일시적 실패를 구분할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublishState {

    private final ProductOutboxRepository productOutboxRepository;
    private final CouponOutboxRepository couponOutboxRepository;

    @Transactional
    public void recordAttempt(UUID outboxId, SagaChannel channel) {
        switch (channel) {
            case PRODUCT -> productOutboxRepository.findById(outboxId)
                    .ifPresent(outbox -> outbox.recordPublishAttempt());
            case COUPON -> couponOutboxRepository.findById(outboxId)
                    .ifPresent(outbox -> outbox.recordPublishAttempt());
        }
    }

    @Transactional
    public void markPublished(UUID outboxId, SagaChannel channel) {
        switch (channel) {
            case PRODUCT -> productOutboxRepository.findById(outboxId)
                    .ifPresent(outbox -> outbox.markPublished());
            case COUPON -> couponOutboxRepository.findById(outboxId)
                    .ifPresent(outbox -> outbox.markPublished());
        }
    }

    /**
     * 발행 실패. STARTED로 되돌려 다음 폴링에서 다시 집게 한다.
     *
     * <p>FAILED로 확정하지 않는다 — 브로커가 잠깐 흔들렸을 뿐인 메시지를 영영 못 나가게 만들면
     * 사가가 조용히 멈춘다. 반복 실패는 {@code publish_attempts}가 알려준다.
     */
    @Transactional
    public void markFailed(UUID outboxId, SagaChannel channel) {
        switch (channel) {
            case PRODUCT -> productOutboxRepository.findById(outboxId)
                    .ifPresent(outbox -> outbox.markPublishFailed());
            case COUPON -> couponOutboxRepository.findById(outboxId)
                    .ifPresent(outbox -> outbox.markPublishFailed());
        }
    }
}
