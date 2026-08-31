package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.repository.CouponOutboxRepository;
import org.coupon.orderservice.repository.ProductOutboxRepository;
import org.coupon.orderservice.saga.SagaChannel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
