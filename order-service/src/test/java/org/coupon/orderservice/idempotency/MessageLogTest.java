package org.coupon.orderservice.idempotency;

import org.coupon.orderservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.idempotency.ConsumedMessageRepository;
import org.coupon.sagapersistence.idempotency.MessageLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlTestContainer.class)
class MessageLogTest {

    @Autowired
    private MessageLog messageLog;

    @Autowired
    private ConsumedMessageRepository consumedMessageRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        consumedMessageRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("트랜잭션 없이 부르면 예외다 — 원장은 도메인 변경과 같은 커밋에 묶여야 한다")
    void requires_existing_transaction() {
        String eventId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> messageLog.alreadyProcessed(eventId))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> messageLog.markProcessed(eventId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("처리 표시 전에는 false, 후에는 true")
    void marks_and_detects() {
        String eventId = UUID.randomUUID().toString();

        Boolean before = transactionTemplate.execute(status -> messageLog.alreadyProcessed(eventId));
        transactionTemplate.executeWithoutResult(status -> messageLog.markProcessed(eventId));
        Boolean after = transactionTemplate.execute(status -> messageLog.alreadyProcessed(eventId));

        assertThat(before).isFalse();
        assertThat(after).isTrue();
        assertThat(consumedMessageRepository.findById(eventId)).isPresent()
                .get().satisfies(row -> assertThat(row.getTimeOfReceipt()).isNotNull());
    }

    @Test
    @DisplayName("처리 표시가 든 트랜잭션이 롤백되면 원장도 남지 않는다")
    void rollback_discards_mark() {
        String eventId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            messageLog.markProcessed(eventId);
            throw new IllegalStateException("도메인 처리 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(consumedMessageRepository.existsById(eventId)).isFalse();
    }
}
