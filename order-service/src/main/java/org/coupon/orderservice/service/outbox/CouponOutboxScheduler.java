package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.coupon.orderservice.kafka.SagaRequestPublisher;
import org.coupon.orderservice.saga.SagaChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 적재된 <b>쿠폰 요청</b>을 Kafka로 내보낸다. 동작과 근거는 {@link ProductOutboxScheduler}와 같다.
 *
 * <p><b>재고 채널은 {@link ProductOutboxScheduler}가 담당한다. 한쪽만 고치지 말 것.</b>
 *
 * <p>주기를 재고와 다르게 주고 싶으면 {@code order-service.outbox.coupon.fixed-delay}로 덮는다.
 * 지정하지 않으면 {@code order-service.outbox.fixed-delay}를 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponOutboxScheduler {

    private final CouponOutboxHelper couponOutboxHelper;
    private final SagaRequestPublisher sagaRequestPublisher;

    @Scheduled(
            fixedDelayString = "${order-service.outbox.coupon.fixed-delay:${order-service.outbox.fixed-delay:5000}}",
            initialDelayString = "${order-service.outbox.coupon.initial-delay:${order-service.outbox.initial-delay:10000}}")
    public void publishPending() {
        List<CouponOutbox> pending = couponOutboxHelper.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        log.info("쿠폰 요청 발행 {}건: {}", pending.size(),
                pending.stream()
                        .map(CouponOutbox::getId)
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        pending.forEach(outbox -> sagaRequestPublisher.publish(outbox.getId(), SagaChannel.COUPON));
    }
}
