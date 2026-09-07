package org.coupon.couponservice.service;

import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.event.RequestType;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.messaging.CouponApplyHandler;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.coupon.couponservice.support.MySqlTestContainer;
import org.coupon.sagapersistence.idempotency.ConsumedMessageRepository;
import org.coupon.sagapersistence.outbox.OutboxEvent;
import org.coupon.sagapersistence.outbox.OutboxEventRepository;
import org.coupon.sagapersistence.outbox.SagaPayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainer.class)
class CouponSagaServiceTest {

    private static final long USER_ID = 42L;
    private static final long ORDER_ID = 100L;
    private static final long ORDER_AMOUNT = 100_000L;
    private static final String SAGA_ID = "saga-1";

    @Autowired
    private CouponApplyHandler couponApplyHandler;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ConsumedMessageRepository consumedMessageRepository;

    @Autowired
    private SagaPayloadCodec sagaPayloadCodec;

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAllInBatch();
        consumedMessageRepository.deleteAllInBatch();
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("쿠폰 적용 (REQUEST)")
    class Apply {

        @Test
        @DisplayName("쿠폰 적용에 성공하면 쿠폰 상태, 처리 원장, 응답 outbox 가 함께 저장된다")
        void applyCouponSavesStatusLedgerAndOutboxTogether() {
            // given
            Long couponId = saveCoupon();
            IssuedCoupon issued = issueCoupon(couponId);
            String eventId = UUID.randomUUID().toString();

            // when
            couponApplyHandler.handle(SAGA_ID, eventId, requestBody(couponId, RequestType.REQUEST));

            // then
            IssuedCoupon after = reload(issued);
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(after.getOrderId()).isEqualTo(ORDER_ID);

            assertThat(consumedMessageRepository.existsById(eventId)).isTrue();

            assertThat(outboxEventRepository.count()).isEqualTo(1);
            CouponApplyResponsePayload response = onlyResponse();
            assertThat(response.result()).isEqualTo(CouponResult.APPLIED);
            assertThat(response.discountAmount()).isEqualTo(10_000L);
            assertThat(response.finalAmount()).isEqualTo(90_000L);
        }

        @Test
        @DisplayName("같은 eventId 로 다시 받아도 쿠폰은 한 번만 소진되고 응답도 한 건이다")
        void sameEventIdIsProcessedOnce() {
            // given
            Long couponId = saveCoupon();
            IssuedCoupon issued = issueCoupon(couponId);
            String eventId = UUID.randomUUID().toString();
            String body = requestBody(couponId, RequestType.REQUEST);
            couponApplyHandler.handle(SAGA_ID, eventId, body);

            // when
            couponApplyHandler.handle(SAGA_ID, eventId, body);

            // then
            assertThat(reload(issued).getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(consumedMessageRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("쿠폰 복구 (CANCEL)")
    class Cancel {

        @Test
        @DisplayName("취소 요청을 받으면 사용한 쿠폰이 다시 사용 가능한 상태로 복구된다")
        void cancelRestoresCoupon() {
            // given
            Long couponId = saveCoupon();
            IssuedCoupon issued = issueCoupon(couponId);
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), requestBody(couponId, RequestType.REQUEST));
            outboxEventRepository.deleteAllInBatch();

            // when
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), requestBody(couponId, RequestType.CANCEL));

            // then
            IssuedCoupon after = reload(issued);
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
            assertThat(after.getOrderId()).isNull();
            assertThat(onlyResponse().result()).isEqualTo(CouponResult.CANCELLED);
        }

        @Test
        @DisplayName("같은 취소 요청을 다시 받아도 복구는 한 번만 일어나고 응답도 한 건이다")
        void sameCancelIsProcessedOnce() {
            // given
            Long couponId = saveCoupon();
            IssuedCoupon issued = issueCoupon(couponId);
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), requestBody(couponId, RequestType.REQUEST));
            outboxEventRepository.deleteAllInBatch();

            String cancelEventId = UUID.randomUUID().toString();
            String cancelBody = requestBody(couponId, RequestType.CANCEL);
            couponApplyHandler.handle(SAGA_ID, cancelEventId, cancelBody);

            // when
            couponApplyHandler.handle(SAGA_ID, cancelEventId, cancelBody);

            // then
            assertThat(reload(issued).getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(onlyResponse().result()).isEqualTo(CouponResult.CANCELLED);
        }
    }

    private Long saveCoupon() {
        return couponRepository.save(Coupon.builder()
                .title("10% 할인")
                .totalQuantity(100L)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .eventEndAt(LocalDateTime.now().plusDays(1))
                .build()).getId();
    }

    private IssuedCoupon issueCoupon(Long couponId) {
        return issuedCouponRepository.save(
                IssuedCoupon.builder().userId(USER_ID).couponId(couponId).build());
    }

    private String requestBody(Long couponId, RequestType type) {
        return sagaPayloadCodec.serialize(
                new CouponApplyRequestPayload(ORDER_ID, USER_ID, couponId, ORDER_AMOUNT, type));
    }

    private IssuedCoupon reload(IssuedCoupon issued) {
        return issuedCouponRepository.findById(issued.getId()).orElseThrow();
    }

    private CouponApplyResponsePayload onlyResponse() {
        OutboxEvent event = outboxEventRepository.findAll().get(0);
        return sagaPayloadCodec.deserialize(event.getPayload(), CouponApplyResponsePayload.class);
    }
}
