package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.coupon.orderservice.kafka.SagaRequestPublisher;
import org.coupon.orderservice.saga.SagaChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 적재된 <b>재고 요청</b>을 Kafka로 내보낸다. 이 폴링이 재고 요청의 <b>유일한 발행 경로</b>다.
 *
 * <p>사가 스텝은 Outbox에 적재만 하고 Kafka를 부르지 않는다. 그래서 여기 걸리는 것은
 * "아직 안 보낸 요청" 전부이며, 다음 두 경우가 같은 상태로 수렴한다.
 * <ul>
 *   <li>방금 적재돼 아직 한 번도 발행되지 않은 row</li>
 *   <li>발행을 시도했으나 브로커가 받지 못해 STARTED로 되돌아온 row</li>
 * </ul>
 * 커밋과 발행을 한 트랜잭션에 묶을 수 없다는 사실을 "발행은 나중에, 상태만 남긴다"로 뒤집은 것이
 * Outbox 패턴이고, 이 클래스가 그 "나중에"다.
 *
 * <p><b>쿠폰 채널은 {@link CouponOutboxScheduler}가 담당한다. 한쪽만 고치지 말 것.</b>
 * 쿠폰 발행을 빠뜨리면 정방향은 멀쩡히 도는데 보상만 조용히 나가지 않는다.
 *
 * <p>메서드에 {@code @Transactional}을 붙이지 않는다. 여기에는 쓰기가 한 줄도 없고
 * (조회는 리포지토리 쿼리, 상태 변경은 {@link OutboxPublishState}가 자기 트랜잭션에서 한다)
 * 붙이면 루프 도는 동안 DB 커넥션만 붙잡는다.
 *
 * <p>발행 콜백이 폴링 주기보다 늦으면 같은 row를 한 번 더 보낼 수 있다. Kafka가 at-least-once인
 * 이상 피할 수 없는 일이고, 참여자가 {@code (sagaId, requestStatus)}로 멱등하게 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductOutboxScheduler {

    private final ProductOutboxHelper productOutboxHelper;
    private final SagaRequestPublisher sagaRequestPublisher;

    /**
     * {@code fixedDelay}만 쓴다. {@code fixedRate}는 한 배치가 밀리면 다음 배치가 겹쳐 돌아
     * 같은 row를 두 스레드가 동시에 발행한다.
     */
    @Scheduled(
            fixedDelayString = "${order-service.outbox.product.fixed-delay:${order-service.outbox.fixed-delay:5000}}",
            initialDelayString = "${order-service.outbox.product.initial-delay:${order-service.outbox.initial-delay:10000}}")
    public void publishPending() {
        List<ProductOutbox> pending = productOutboxHelper.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        // 대상 id를 발행 '직전에' 남긴다. 발행 도중 프로세스가 죽으면 성공/실패 로그가
        // 둘 다 안 남으므로, 무엇을 보내려 했는지 아는 단서가 이 줄뿐이다.
        log.info("재고 요청 발행 {}건: {}", pending.size(),
                pending.stream()
                        .map(ProductOutbox::getId)
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        pending.forEach(outbox -> sagaRequestPublisher.publish(outbox.getId(), SagaChannel.PRODUCT));
    }
}
