package org.coupon.couponservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class CouponIssueMetrics {

    private final Counter requested;
    private final Counter accepted;
    private final Counter rejectedSoldOut;
    private final Counter rejectedAlreadyIssued;
    private final Counter rejectedEventEnded;
    private final Counter messageProduced;
    private final Counter messageProduceFailed;
    private final Counter persisted;
    private final Counter duplicateMessage;
    private final Counter consumeFailed;
    private final Timer decision;
    private final Timer persistence;

    public CouponIssueMetrics(MeterRegistry registry) {
        this.requested = Counter.builder("coupon.issue.requested")
                .description("발급 API까지 들어온 요청 수")
                .register(registry);
        this.accepted = Counter.builder("coupon.issue.accepted")
                .description("Redis가 신규 발급을 허용한 수")
                .register(registry);
        this.rejectedSoldOut = rejected(registry, "sold_out");
        this.rejectedAlreadyIssued = rejected(registry, "already_issued");
        this.rejectedEventEnded = rejected(registry, "event_ended");

        this.messageProduced = Counter.builder("coupon.issue.message.produced")
                .description("브로커가 발급 메시지를 받은 수")
                .register(registry);
        this.messageProduceFailed = Counter.builder("coupon.issue.message.produce_failed")
                .description("Redis 승인 후 Kafka 전송에 실패한 수")
                .register(registry);
        this.persisted = Counter.builder("coupon.issue.persisted")
                .description("DB 커밋까지 끝난 최종 발급 완료 수")
                .register(registry);
        this.duplicateMessage = Counter.builder("coupon.issue.duplicate_message")
                .description("Kafka 재전달로 안전하게 무시한 수")
                .register(registry);
        this.consumeFailed = Counter.builder("coupon.issue.consume_failed")
                .description("재시도되어야 하는 소비 실패 수")
                .register(registry);

        this.decision = Timer.builder("coupon.issue.decision.duration")
                .description("Redis 판정과 Kafka 전송 요청까지")
                .publishPercentileHistogram()
                .register(registry);
        this.persistence = Timer.builder("coupon.issue.persistence.duration")
                .description("Kafka 소비 후 DB 커밋까지")
                .publishPercentileHistogram()
                .register(registry);
    }

    private static Counter rejected(MeterRegistry registry, String reason) {
        return Counter.builder("coupon.issue.rejected")
                .description("발급 거절 수")
                .tag("reason", reason)
                .register(registry);
    }

    public void requested() {
        requested.increment();
    }

    public void accepted() {
        accepted.increment();
    }

    public void rejectedSoldOut() {
        rejectedSoldOut.increment();
    }

    public void rejectedAlreadyIssued() {
        rejectedAlreadyIssued.increment();
    }

    public void rejectedEventEnded() {
        rejectedEventEnded.increment();
    }

    public void messageProduced() {
        messageProduced.increment();
    }

    public void messageProduceFailed() {
        messageProduceFailed.increment();
    }

    public void persisted() {
        persisted.increment();
    }

    public void duplicateMessage() {
        duplicateMessage.increment();
    }

    public void consumeFailed() {
        consumeFailed.increment();
    }

    public <T> T recordDecision(Supplier<T> issue) {
        return decision.record(issue);
    }

    public void recordPersistence(Runnable persist) {
        persistence.record(persist);
    }
}
