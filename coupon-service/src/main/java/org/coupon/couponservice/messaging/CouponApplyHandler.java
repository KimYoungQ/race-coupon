package org.coupon.couponservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.couponservice.service.CouponSagaService;
import org.coupon.sagapersistence.idempotency.MessageLog;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponApplyHandler {

    private final MessageLog messageLog;
    private final CouponSagaService couponSagaService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 쿠폰 요청을 처리한다.
     * 이미 처리한 요청이면 건너뛴다.
     * 처리 결과는 응답 이벤트로 발행한다.
     *
     * @return 처리 결과. 이미 처리한 요청이면 null
     */
    @Transactional
    public CouponApplyResponsePayload handle(String sagaId, String eventId, CouponApplyRequestPayload request) {
        if (messageLog.alreadyProcessed(eventId)) {
            log.info("이미 처리한 쿠폰 요청, 건너뛴다: sagaId={}, eventId={}", sagaId, eventId);
            return null;
        }

        CouponApplyResponsePayload response = switch (request.type()) {
            case REQUEST -> couponSagaService.apply(request);
            case CANCEL -> couponSagaService.cancel(request);
        };

        eventPublisher.publishEvent(new CouponResponded(sagaId, response));
        messageLog.markProcessed(eventId);

        log.info("쿠폰 요청 처리: sagaId={}, eventId={}, type={}, orderId={}, result={}, failureCode={}",
                sagaId, eventId, request.type(), request.orderId(), response.result(), response.failureCode());
        return response;
    }
}
