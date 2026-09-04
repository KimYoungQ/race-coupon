package org.coupon.sagapersistence.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Getter
@Entity
@Table(name = "consumed_message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedMessage implements Persistable<String> {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "time_of_receipt", updatable = false)
    private Instant timeOfReceipt;

    @Transient
    private boolean isNew = true;

    ConsumedMessage(String eventId) {
        this.eventId = eventId;
        this.timeOfReceipt = Instant.now();
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
