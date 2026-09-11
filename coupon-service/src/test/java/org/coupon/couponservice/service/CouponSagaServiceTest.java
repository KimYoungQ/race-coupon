package org.coupon.couponservice.service;

import org.coupon.common.event.CouponApplyRequestPayload;
import org.coupon.common.event.CouponApplyResponsePayload;
import org.coupon.common.event.CouponResult;
import org.coupon.common.event.RequestType;
import org.coupon.common.exception.ErrorCode;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
            couponApplyHandler.handle(SAGA_ID, eventId, request(couponId, RequestType.REQUEST));

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
            CouponApplyRequestPayload applyRequest = request(couponId, RequestType.REQUEST);
            couponApplyHandler.handle(SAGA_ID, eventId, applyRequest);

            // when
            couponApplyHandler.handle(SAGA_ID, eventId, applyRequest);

            // then
            assertThat(reload(issued).getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(consumedMessageRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 주문이 다른 eventId 로 다시 요청하면 거절되고 쿠폰은 다시 묶이지 않는다")
        void sameOrderIsRejectedOnRetry() {
            // given
            Long couponId = saveCoupon();
            IssuedCoupon issued = issueCoupon(couponId);
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), request(couponId, RequestType.REQUEST));
            outboxEventRepository.deleteAllInBatch();

            // when
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), request(couponId, RequestType.REQUEST));

            // then
            CouponApplyResponsePayload response = onlyResponse();
            assertThat(response.result()).isEqualTo(CouponResult.REJECTED);
            assertThat(response.failureCode()).isEqualTo(ErrorCode.COUPON_ALREADY_USED.getCode());
            assertThat(reload(issued).getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("같은 발급 쿠폰을 여러 주문이 동시에 사용해도 한 주문만 적용된다")
        void onlyOneOrderAppliesCoupon() throws InterruptedException {
            // given
            Long couponId = saveCoupon();
            IssuedCoupon issued = issueCoupon(couponId);
            int orderCount = 10;

            // when
            ExecutorService executor = Executors.newFixedThreadPool(orderCount);
            CountDownLatch latch = new CountDownLatch(orderCount);
            for (int i = 0; i < orderCount; i++) {
                long orderId = 1000L + i;
                executor.submit(() -> {
                    try {
                        couponApplyHandler.handle(
                                "saga-" + orderId,
                                UUID.randomUUID().toString(),
                                request(orderId, couponId, RequestType.REQUEST));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then
            List<CouponApplyResponsePayload> responses = allResponses();
            assertThat(responses).hasSize(orderCount);

            List<CouponApplyResponsePayload> applied = responses.stream()
                    .filter(response -> response.result() == CouponResult.APPLIED)
                    .toList();
            assertThat(applied).hasSize(1);

            assertThat(responses)
                    .filteredOn(response -> response.result() == CouponResult.REJECTED)
                    .hasSize(orderCount - 1)
                    .allSatisfy(response -> assertThat(response.failureCode())
                            .isEqualTo(ErrorCode.COUPON_ALREADY_USED.getCode()));

            IssuedCoupon after = reload(issued);
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(after.getOrderId()).isEqualTo(applied.get(0).orderId());
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
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), request(couponId, RequestType.REQUEST));
            outboxEventRepository.deleteAllInBatch();

            // when
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), request(couponId, RequestType.CANCEL));

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
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), request(couponId, RequestType.REQUEST));
            outboxEventRepository.deleteAllInBatch();

            String cancelEventId = UUID.randomUUID().toString();
            CouponApplyRequestPayload cancelRequest = request(couponId, RequestType.CANCEL);
            couponApplyHandler.handle(SAGA_ID, cancelEventId, cancelRequest);

            // when
            couponApplyHandler.handle(SAGA_ID, cancelEventId, cancelRequest);

            // then
            assertThat(reload(issued).getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(onlyResponse().result()).isEqualTo(CouponResult.CANCELLED);
        }

        @Test
        @DisplayName("다른 주문의 취소는 쿠폰 사용을 해제하지 않는다")
        void cancelFromAnotherOrderKeepsCouponUsed() {
            // given
            Long couponId = saveCoupon();
            IssuedCoupon issued = issueCoupon(couponId);
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(), request(couponId, RequestType.REQUEST));
            outboxEventRepository.deleteAllInBatch();

            // when
            couponApplyHandler.handle(SAGA_ID, UUID.randomUUID().toString(),
                    request(999L, couponId, RequestType.CANCEL));

            // then
            IssuedCoupon after = reload(issued);
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(after.getOrderId()).isEqualTo(ORDER_ID);
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

    private CouponApplyRequestPayload request(Long couponId, RequestType type) {
        return request(ORDER_ID, couponId, type);
    }

    private CouponApplyRequestPayload request(Long orderId, Long couponId, RequestType type) {
        return new CouponApplyRequestPayload(orderId, USER_ID, couponId, ORDER_AMOUNT, type);
    }

    private IssuedCoupon reload(IssuedCoupon issued) {
        return issuedCouponRepository.findById(issued.getId()).orElseThrow();
    }

    private CouponApplyResponsePayload onlyResponse() {
        OutboxEvent event = outboxEventRepository.findAll().get(0);
        return sagaPayloadCodec.deserialize(event.getPayload(), CouponApplyResponsePayload.class);
    }

    private List<CouponApplyResponsePayload> allResponses() {
        return outboxEventRepository.findAll().stream()
                .map(event -> sagaPayloadCodec.deserialize(event.getPayload(), CouponApplyResponsePayload.class))
                .toList();
    }
}
