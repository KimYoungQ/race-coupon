package org.coupon.couponservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.couponservice.domain.outbox.OrderOutbox;
import org.coupon.couponservice.messaging.CouponResponsePublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 적재된 쿠폰 응답을 order-service로 내보낸다. 응답의 <b>유일한 발행 경로</b>다.
 *
 * <p>여기 걸리는 것은 두 종류다.
 * <ul>
 *   <li>방금 처리돼 아직 한 번도 발행되지 않은 응답</li>
 *   <li>중복 요청을 받아 <b>다시 보내기로 표시된</b> 응답 — 오케스트레이터가 앞선 응답을
 *       받지 못했다는 뜻이므로, 도메인을 다시 실행하는 대신 같은 결과를 다시 보낸다</li>
 * </ul>
 *
 * <p>메서드에 {@code @Transactional}을 붙이지 않는다. 쓰기가 한 줄도 없고
 * (조회는 리포지토리 쿼리, 상태 변경은 {@link OrderOutboxPublishState}가 자기 트랜잭션에서 한다)
 * 붙이면 루프 도는 동안 DB 커넥션만 붙잡는다.
 *
 * <p><b>이 스케줄러는 주문 사가 전용이다.</b> 선착순 발급 경로에는 Outbox를 넣지 않는다 —
 * 요청당 INSERT가 하나 늘면 그동안 쌓은 k6 수치가 무너진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxScheduler {

    private final OrderOutboxHelper orderOutboxHelper;
    private final CouponResponsePublisher couponResponsePublisher;

    /**
     * {@code fixedDelay}만 쓴다. {@code fixedRate}는 한 배치가 밀리면 다음 배치가 겹쳐 돌아
     * 같은 row를 두 스레드가 동시에 발행한다.
     */
    @Scheduled(fixedDelayString = "${coupon-service.outbox.fixed-delay:5000}",
            initialDelayString = "${coupon-service.outbox.initial-delay:10000}")
    public void publishPending() {
        List<OrderOutbox> pending = orderOutboxHelper.findUnpublished();
        if (pending.isEmpty()) {
            return;
        }

        // 대상 id를 발행 '직전에' 남긴다. 발행 도중 프로세스가 죽으면 성공/실패 로그가
        // 둘 다 안 남으므로, 무엇을 보내려 했는지 아는 단서가 이 줄뿐이다.
        log.info("쿠폰 응답 발행 {}건: {}", pending.size(),
                pending.stream()
                        .map(OrderOutbox::getId)
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        pending.forEach(outbox -> couponResponsePublisher.publish(outbox.getId()));
    }
}
