package org.coupon.couponservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.repository.OrderOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 발행 결과를 응답 Outbox row에 반영한다.
 *
 * <p>별도 빈으로 분리한 이유는 두 가지다.
 * <ul>
 *   <li>Kafka 발행 콜백은 <b>다른 스레드</b>에서 트랜잭션 없이 실행된다. 여기서 새로 열어야 한다</li>
 *   <li>같은 클래스 안에서 호출하면 프록시를 거치지 않아 {@code @Transactional}이 무시된다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OrderOutboxPublishState {

    private final OrderOutboxRepository orderOutboxRepository;

    /** 발행 <b>전에</b> 커밋해 둔다. 도중에 죽어도 몇 번 시도했는지가 남아야 진단이 된다. */
    @Transactional
    public void recordAttempt(UUID outboxId) {
        orderOutboxRepository.findById(outboxId).ifPresent(outbox -> outbox.recordPublishAttempt());
    }

    @Transactional
    public void markPublished(UUID outboxId) {
        orderOutboxRepository.findById(outboxId).ifPresent(outbox -> outbox.markPublished());
    }

    /** 실패는 STARTED로 되돌려 다음 폴링이 다시 집게 한다. FAILED로 확정하면 사가가 멈춘다. */
    @Transactional
    public void markFailed(UUID outboxId) {
        orderOutboxRepository.findById(outboxId).ifPresent(outbox -> outbox.markPublishFailed());
    }
}
