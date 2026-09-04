package org.coupon.couponservice.service;

import org.coupon.common.event.AggregateTypes;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Import(MySqlTestContainer.class)
class CouponSagaServiceTest {

    private static final long USER_ID = 42L;
    private static final long ORDER_ID = 100L;
    private static final long ORDER_AMOUNT = 100_000L;

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

    private Long couponId;
    private String sagaId;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        consumedMessageRepository.deleteAllInBatch();
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();

        couponId = saveCoupon(null);
        sagaId = UUID.randomUUID().toString();
    }

    private Long saveCoupon(Long minOrderAmount) {
        return couponRepository.save(Coupon.builder()
                .title("10% 할인")
                .totalQuantity(100L)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .minOrderAmount(minOrderAmount)
                .eventEndAt(LocalDateTime.now().plusDays(1))
                .build()).getId();
    }

    private IssuedCoupon issue(Long couponId) {
        return issuedCouponRepository.save(
                IssuedCoupon.builder().userId(USER_ID).couponId(couponId).build());
    }

    private String body(Long orderId, Long couponId, long orderAmount, RequestType type) {
        return sagaPayloadCodec.serialize(
                new CouponApplyRequestPayload(orderId, USER_ID, couponId, orderAmount, type));
    }

    private String handle(RequestType type, Long couponId, long orderAmount) {
        return handle(ORDER_ID, type, couponId, orderAmount);
    }

    private String handle(Long orderId, RequestType type, Long couponId, long orderAmount) {
        String eventId = UUID.randomUUID().toString();
        couponApplyHandler.handle(sagaId, eventId, body(orderId, couponId, orderAmount, type));
        return eventId;
    }

    private OutboxEvent onlyOutbox() {
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        return outboxEventRepository.findAll().get(0);
    }

    private CouponApplyResponsePayload responseOf(OutboxEvent event) {
        return sagaPayloadCodec.deserialize(event.getPayload(), CouponApplyResponsePayload.class);
    }

    private IssuedCoupon reload(IssuedCoupon issued) {
        return issuedCouponRepository.findById(issued.getId()).orElseThrow();
    }

    @Nested
    @DisplayName("쿠폰 적용(REQUEST)")
    class Apply {

        @Test
        @DisplayName("발급 건을 소진하고 금액을 계산하며, 응답 outbox 와 처리 원장이 같은 트랜잭션에 남는다")
        void applies_and_records_response_outbox() {
            IssuedCoupon issued = issue(couponId);

            String eventId = handle(RequestType.REQUEST, couponId, ORDER_AMOUNT);

            IssuedCoupon after = reload(issued);
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(after.getOrderId()).isEqualTo(ORDER_ID);

            OutboxEvent event = onlyOutbox();
            assertThat(event.getAggregateType()).isEqualTo(AggregateTypes.COUPON_APPLY);
            assertThat(event.getAggregateId()).isEqualTo(sagaId);
            assertThat(event.getType()).isEqualTo("APPLIED");
            CouponApplyResponsePayload response = responseOf(event);
            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            assertThat(response.result()).isEqualTo(CouponResult.APPLIED);
            assertThat(response.discountAmount()).isEqualTo(10_000L);
            assertThat(response.finalAmount()).isEqualTo(90_000L);
            assertThat(response.failureCode()).isNull();

            assertThat(consumedMessageRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("할인액과 최종금액을 더하면 주문금액이 된다 — 주문 이력이 어긋나지 않는다")
        void amounts_are_consistent() {
            issue(couponId);

            handle(RequestType.REQUEST, couponId, ORDER_AMOUNT);

            CouponApplyResponsePayload response = responseOf(onlyOutbox());
            assertThat(response.discountAmount() + response.finalAmount()).isEqualTo(ORDER_AMOUNT);
        }

        @Test
        @DisplayName("발급이 아직 반영되지 않았으면 REJECTED + COUPON_NOT_ISSUED_YET 로 답한다")
        void not_issued_yet() {
            assertThatCode(() -> handle(RequestType.REQUEST, couponId, ORDER_AMOUNT))
                    .doesNotThrowAnyException();

            CouponApplyResponsePayload response = responseOf(onlyOutbox());
            assertThat(response.result()).isEqualTo(CouponResult.REJECTED);
            assertThat(response.failureCode()).isEqualTo(ErrorCode.COUPON_NOT_ISSUED_YET.getCode());
            assertThat(response.discountAmount()).isNull();
            assertThat(response.finalAmount()).isNull();
        }

        @Test
        @DisplayName("이미 사용된 쿠폰은 예외가 아니라 REJECTED + COUPON_ALREADY_USED 이고 원 주문은 유지된다")
        void already_used_becomes_rejection_response() {
            IssuedCoupon issued = issue(couponId);
            handle(RequestType.REQUEST, couponId, ORDER_AMOUNT);
            outboxEventRepository.deleteAllInBatch();

            assertThatCode(() -> handle(999L, RequestType.REQUEST, couponId, ORDER_AMOUNT))
                    .doesNotThrowAnyException();

            CouponApplyResponsePayload response = responseOf(onlyOutbox());
            assertThat(response.result()).isEqualTo(CouponResult.REJECTED);
            assertThat(response.failureCode()).isEqualTo(ErrorCode.COUPON_ALREADY_USED.getCode());
            assertThat(reload(issued).getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("최소 주문 금액 미달은 조용한 할인 0이 아니라 REJECTED 이고 쿠폰은 ISSUED 로 남는다")
        void min_order_amount_not_met_fails_explicitly() {
            Long gatedCouponId = saveCoupon(50_000L);
            IssuedCoupon issued = issue(gatedCouponId);

            handle(RequestType.REQUEST, gatedCouponId, 10_000L);

            CouponApplyResponsePayload response = responseOf(onlyOutbox());
            assertThat(response.result()).isEqualTo(CouponResult.REJECTED);
            assertThat(response.failureCode())
                    .isEqualTo(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET.getCode());
            assertThat(reload(issued).getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        }
    }

    @Nested
    @DisplayName("멱등성 — 같은 메시지(id 헤더)의 재전송")
    class Idempotency {

        @Test
        @DisplayName("같은 eventId 로 두 번 받아도 쿠폰은 한 번만 소진되고 응답도 한 건이다")
        void same_event_id_is_processed_once() {
            IssuedCoupon issued = issue(couponId);
            String eventId = UUID.randomUUID().toString();
            String body = body(ORDER_ID, couponId, ORDER_AMOUNT, RequestType.REQUEST);

            couponApplyHandler.handle(sagaId, eventId, body);
            couponApplyHandler.handle(sagaId, eventId, body);

            assertThat(reload(issued).getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(consumedMessageRepository.count()).isEqualTo(1);
            assertThat(responseOf(onlyOutbox()).result()).isEqualTo(CouponResult.APPLIED);
        }
    }

    @Nested
    @DisplayName("쿠폰 복구(CANCEL)")
    class Cancel {

        @Test
        @DisplayName("사용을 되돌리면 다시 쓸 수 있는 상태가 되고 CANCELLED 로 답한다")
        void cancels_coupon() {
            IssuedCoupon issued = issue(couponId);
            handle(RequestType.REQUEST, couponId, ORDER_AMOUNT);
            outboxEventRepository.deleteAllInBatch();

            handle(RequestType.CANCEL, couponId, ORDER_AMOUNT);

            IssuedCoupon after = reload(issued);
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
            assertThat(after.getOrderId()).isNull();
            OutboxEvent event = onlyOutbox();
            assertThat(event.getType()).isEqualTo("CANCELLED");
            assertThat(responseOf(event).result()).isEqualTo(CouponResult.CANCELLED);
        }

        @Test
        @DisplayName("되돌릴 발급 건이 없어도 CANCELLED 로 답한다 — 실패로 답하면 보상이 끝나지 못한다")
        void cancel_without_issued_coupon_still_succeeds() {
            handle(RequestType.CANCEL, couponId, ORDER_AMOUNT);

            assertThat(responseOf(onlyOutbox()).result()).isEqualTo(CouponResult.CANCELLED);
        }
    }
}
