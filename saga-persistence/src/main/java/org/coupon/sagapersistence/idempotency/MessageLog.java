package org.coupon.sagapersistence.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MessageLog {

    private final ConsumedMessageRepository consumedMessageRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean alreadyProcessed(String eventId) {
        return consumedMessageRepository.existsById(eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(String eventId) {
        consumedMessageRepository.save(new ConsumedMessage(eventId));
    }
}
