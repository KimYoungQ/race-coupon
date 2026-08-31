package org.coupon.couponservice.service.outbox;

import lombok.RequiredArgsConstructor;
import org.coupon.couponservice.repository.OrderOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderOutboxPublishState {

    private final OrderOutboxRepository orderOutboxRepository;

    @Transactional
    public void recordAttempt(UUID outboxId) {
        orderOutboxRepository.findById(outboxId).ifPresent(outbox -> outbox.recordPublishAttempt());
    }

    @Transactional
    public void markPublished(UUID outboxId) {
        orderOutboxRepository.findById(outboxId).ifPresent(outbox -> outbox.markPublished());
    }

    @Transactional
    public void markFailed(UUID outboxId) {
        orderOutboxRepository.findById(outboxId).ifPresent(outbox -> outbox.markPublishFailed());
    }
}
