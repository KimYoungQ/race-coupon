package org.coupon.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.event.SagaTopics;
import org.coupon.common.event.StockRequest;
import org.coupon.orderservice.saga.SagaChannel;
import org.coupon.orderservice.service.outbox.CouponOutboxHelper;
import org.coupon.orderservice.service.outbox.OutboxPublishState;
import org.coupon.orderservice.service.outbox.ProductOutboxHelper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Saga 요청을 Kafka로 내보낸다. 진실의 원천은 어디까지나 Outbox row이고, 여기는 그 row를 실어 나른다.
 *
 * <p><b>호출자는 스케줄러뿐이다.</b> 사가 스텝은 Outbox에 적재만 하고 Kafka를 직접 부르지 않는다.
 * 커밋 전에 보내면 이후 롤백된 요청이 이미 나가버려 참여자가 존재하지 않는 주문의 재고를 깎고,
 * 커밋 직후에 보내면 그 사이 프로세스가 죽었을 때 요청이 영영 나가지 않는다.
 * 발행 시점을 스케줄러 한 곳으로 모으면 두 경우가 모두 "STARTED로 남은 row"라는 한 가지 상태로 수렴한다.
 *
 * <p>대가는 지연이다. 주문 접수부터 재고 요청 발행까지 최대 폴링 주기만큼 걸린다.
 * 주문 응답이 202(접수)이고 결과를 조회로 확인하는 구조라 받아들일 수 있는 비용이다.
 *
 * <p>Outbox row 자체가 아니라 <b>id를 받는다.</b> 발행 콜백은 다른 스레드에서 실행되는데,
 * 그때 스케줄러의 조회 트랜잭션은 이미 끝나 있다. 엔티티를 들고 건너가면 detached 인스턴스를
 * 고쳐 merge하는 모양이 되고, 그 사이 응답 리스너가 같은 row를 전진시켰으면
 * 낙관적 락에 걸린다. 새 트랜잭션에서 새로 읽는 편이 창이 짧다.
 *
 * <p><b>{@code KafkaTemplate} 빈을 새로 선언하지 않는다.</b> Boot의 {@code KafkaAutoConfiguration}은
 * 이 타입을 {@code @ConditionalOnMissingBean(KafkaTemplate.class)}으로 정의한다 — 이름이 아니라
 * <b>타입</b> 기준이라, 사가 전용 템플릿을 올리는 순간 기본 빈이 사라지고 다른 프로듀서가 깨진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRequestPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProductOutboxHelper productOutboxHelper;
    private final CouponOutboxHelper couponOutboxHelper;
    private final OutboxPublishState outboxPublishState;

    /**
     * 한 건을 발행한다. <b>예외를 밖으로 내보내지 않는다.</b>
     *
     * <p>스케줄러가 배치를 {@code forEach}로 도는데, 여기서 예외가 새어 나가면 그 뒤의 row들은
     * 이번 틱에 아예 발행되지 않는다. 게다가 payload가 깨진 row처럼 <b>매번 같은 자리에서 실패하는
     * 건이 하나 있으면 그 뒤에 줄 선 요청은 영원히 못 나간다.</b> 한 건의 실패가 다른 건을
     * 볼모로 잡지 않도록 여기서 끊는다.
     *
     * <p>삼켜도 유실되지 않는다. Outbox row는 성공 콜백에서만 COMPLETED가 되므로
     * 실패한 건은 STARTED로 남아 다음 폴링이 다시 집는다.
     *
     * <p>다만 계속 실패하는 row는 매 틱 재시도되며 로그만 쌓인다. 상한을 두고 자동으로
     * 손을 떼는 동작은 DLT와 함께 별도로 설계할 대상이고, 그 전까지는
     * {@code publish_attempts}가 그런 row를 찾는 단서다.
     *
     * <p>여기 catch는 <b>발행에 이르기 전</b>의 실패(row 조회, payload 역직렬화)를 받는다.
     * 그 단계에서는 사가를 특정할 값이 없어 {@code outboxId}밖에 남길 것이 없다.
     * 발행 자체의 실패는 {@link #publishProduct(UUID)}/{@link #publishCoupon(UUID)} 안쪽에서
     * 사가 맥락과 함께 잡는다.
     */
    public void publish(UUID outboxId, SagaChannel channel) {
        try {
            switch (channel) {
                case PRODUCT -> publishProduct(outboxId);
                case COUPON -> publishCoupon(outboxId);
            }
        } catch (Exception e) {
            log.error("Saga 요청 발행 준비 실패(row 조회 또는 payload 역직렬화): outboxId={}, channel={}",
                    outboxId, channel, e);
        }
    }

    /**
     * payload를 요청으로 되돌린 <b>뒤</b>에 try를 연다. 역직렬화까지 감싸지 않는 것은 의도적이다 —
     * 그 단계가 깨지면 {@code sagaId}·{@code orderId}를 알 방법이 없어 여기서 잡아봐야
     * 바깥 catch보다 나은 로그를 남길 수 없다. 대신 {@code send}가 실패했을 때는
     * 사가를 특정할 수 있는 값들이 손에 있으므로 그것들을 남긴다.
     */
    private void publishProduct(UUID outboxId) {
        productOutboxHelper.findById(outboxId).ifPresent(outbox -> {
            StockRequest request = productOutboxHelper.toRequest(outbox);

            // 브로커에 넘기기 전에 사가 맥락을 남긴다. 성공/실패 콜백은 비동기라 순서가 뒤섞이고,
            // 발행 도중 프로세스가 죽으면 그쪽 로그는 아예 안 남는다.
            log.info("재고 요청 발행 시도: sagaId={}, orderId={}, requestStatus={}, outboxId={}",
                    request.sagaId(), request.orderId(), request.stockOrderStatus(), outboxId);

            try {
                send(SagaTopics.PRODUCT_REQUEST, stockKey(request), request, outboxId, SagaChannel.PRODUCT);
            } catch (Exception e) {
                log.error("재고 요청 발행 실패: sagaId={}, orderId={}, productId={}, outboxId={}",
                        request.sagaId(), request.orderId(), request.productId(), outboxId, e);
            }
        });
    }

    /** try 위치와 근거는 {@link #publishProduct(UUID)}와 같다. */
    private void publishCoupon(UUID outboxId) {
        couponOutboxHelper.findById(outboxId).ifPresent(outbox -> {
            CouponRequest request = couponOutboxHelper.toRequest(outbox);

            log.info("쿠폰 요청 발행 시도: sagaId={}, orderId={}, requestStatus={}, outboxId={}",
                    request.sagaId(), request.orderId(), request.couponOrderStatus(), outboxId);

            try {
                send(SagaTopics.COUPON_REQUEST, couponKey(request), request, outboxId, SagaChannel.COUPON);
            } catch (Exception e) {
                log.error("쿠폰 요청 발행 실패: sagaId={}, orderId={}, couponId={}, outboxId={}",
                        request.sagaId(), request.orderId(), request.couponId(), outboxId, e);
            }
        });
    }

    /**
     * 재고 요청의 파티션 키 = 경합 자원의 소유자.
     *
     * <p>같은 상품의 RESERVE와 RESTORE가 한 파티션에서 직렬화되어야 {@code Product}가
     * DB 락 없이도 안전하다. 이 키를 바꾸거나 토픽을 쪼개면 그 전제가 무너진다.
     */
    private String stockKey(StockRequest request) {
        return String.valueOf(request.productId());
    }

    /**
     * 쿠폰 요청의 파티션 키 = {@code userId:couponId}.
     *
     * <p>경합 자원은 {@code IssuedCoupon} row지만 발행 시점에는 그 PK를 모른다
     * (발급 API가 돌려주지 않고, row 자체가 아직 없을 수도 있다).
     * {@code UNIQUE(user_id, coupon_id)}가 그 row를 유일하게 지목하므로 두 값을 합쳐 키로 쓴다.
     * {@code userId} 단독도 안전하지만 같은 사용자의 서로 다른 쿠폰까지 불필요하게 직렬화한다.
     */
    private String couponKey(CouponRequest request) {
        return request.userId() + ":" + request.couponId();
    }

    private void send(String topic, String key, Object payload, UUID outboxId, SagaChannel channel) {
        outboxPublishState.recordAttempt(outboxId, channel);

        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex == null) {
                var metadata = result.getRecordMetadata();
                log.info("Saga 요청 발행: topic={}, key={}, partition={}, offset={}, outboxId={}",
                        topic, key, metadata.partition(), metadata.offset(), outboxId);
                outboxPublishState.markPublished(outboxId, channel);
            } else {
                log.error("Saga 요청 발행 실패: topic={}, key={}, outboxId={}", topic, key, outboxId, ex);
                outboxPublishState.markFailed(outboxId, channel);
            }
        });
    }
}
