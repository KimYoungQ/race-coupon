package org.coupon.sagapersistence.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedMessageRepository extends JpaRepository<ConsumedMessage, String> {
}
