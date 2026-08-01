package org.coupon.couponservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 선착순 발급 경로의 계측. {@code HTTP 요청 → Redis 판정 → Kafka 전송 → DB 발급 완료}
 * 네 경계에서만 값을 올린다.
 *
 * <p>미터를 생성자에서 미리 만들어 필드로 들고 있는다. 발급은 핫패스라
 * {@code registry.counter(name)}처럼 호출마다 이름으로 레지스트리를 뒤지게 두지 않는다.
 * 아직 한 건도 일어나지 않은 사유도 등록 시점에 0으로 노출되므로 대시보드에서 계열이 사라지지 않는다.
 *
 * <p>태그는 {@code reason} 하나뿐이고 값도 세 개로 고정이다. {@code userId}·{@code couponId}처럼
 * 값이 무한히 늘어나는 것을 태그로 쓰면 시계열이 그만큼 생겨 Prometheus가 버티지 못한다.
 */
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

    /**
     * 발급 시도를 <b>판정하는 데</b> 걸린 시간. 성공 시간이 아니다 —
     * {@code Timer#record}가 try/finally라 품절·중복·만료로 거절된 시간도 함께 들어간다.
     */
    public <T> T recordDecision(Supplier<T> issue) {
        return decision.record(issue);
    }

    /**
     * 트랜잭션 경계 <b>바깥</b>에서 감싸야 한다. {@code @Transactional} 프록시는 메서드 본문이
     * 끝난 뒤에 커밋하므로, 본문 안에서 재면 정작 DB에 쓰는 시간이 측정에서 빠진다.
     */
    public void recordPersistence(Runnable persist) {
        persistence.record(persist);
    }
}
