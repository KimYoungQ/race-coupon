package org.coupon.couponservice.service;

import org.coupon.common.event.CouponOrderStatus;
import org.coupon.common.event.CouponRequest;
import org.coupon.common.event.CouponResponse;
import org.coupon.common.event.CouponStatus;
import org.coupon.common.exception.ErrorCode;
import org.coupon.common.outbox.OutboxStatus;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.domain.IssuedCoupon;
import org.coupon.couponservice.domain.IssuedCouponStatus;
import org.coupon.couponservice.domain.outbox.OrderOutbox;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.coupon.couponservice.repository.OrderOutboxRepository;
import org.coupon.couponservice.service.outbox.OrderOutboxHelper;
import org.coupon.couponservice.service.outbox.OrderOutboxPublishState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 사가 참여자로서 coupon-service의 계약을 고정한다.
 *
 * <p>Kafka를 거치지 않고 서비스를 직접 호출한다 — 검증 대상이 메시징이 아니라
 * "요청이 실어온 {@code couponId}로 발급 건을 찾아내는가", "비즈니스 실패가 예외가 아니라 응답이 되는가",
 * "실린 금액이 서로 정합한가"이기 때문이다.
 */
@SpringBootTest
class CouponSagaServiceTest {

    private static final long USER_ID = 42L;
    private static final long ORDER_ID = 100L;
    private static final long ORDER_AMOUNT = 100_000L;

    @Autowired
    private CouponSagaService couponSagaService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private OrderOutboxRepository orderOutboxRepository;

    @Autowired
    private OrderOutboxHelper orderOutboxHelper;

    @Autowired
    private OrderOutboxPublishState orderOutboxPublishState;

    private Long couponId;

    @BeforeEach
    void setUp() {
        orderOutboxRepository.deleteAllInBatch();
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();

        couponId = saveCoupon(null);
    }

    /** 10% 할인 쿠폰. {@code minOrderAmount}가 null이면 최소 금액 조건 없음. */
    private Long saveCoupon(Long minOrderAmount) {
        return couponRepository.save(Coupon.builder()
                .title("10% 할인")
                .totalQuantity(100L)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .minOrderAmount(minOrderAmount)
                .build()).getId();
    }

    private IssuedCoupon issue(Long couponId) {
        return issuedCouponRepository.save(
                IssuedCoupon.builder().userId(USER_ID).couponId(couponId).build());
    }

    private CouponRequest request(CouponOrderStatus status, Long couponId, long orderAmount) {
        return new CouponRequest(UUID.randomUUID(), UUID.randomUUID(), ORDER_ID,
                USER_ID, couponId, orderAmount, status, Instant.now());
    }

    private CouponRequest sameSaga(CouponRequest origin, CouponOrderStatus status) {
        return new CouponRequest(UUID.randomUUID(), origin.sagaId(), origin.orderId(),
                origin.userId(), origin.couponId(), origin.orderAmount(), status, Instant.now());
    }

    private OrderOutbox onlyOutbox() {
        assertThat(orderOutboxRepository.findAll()).hasSize(1);
        return orderOutboxRepository.findAll().get(0);
    }

    private CouponResponse responseOf(OrderOutbox outbox) {
        return orderOutboxHelper.toResponse(outbox);
    }

    @Nested
    @DisplayName("쿠폰 적용(PENDING)")
    class Apply {

        @Test
        @DisplayName("couponId로 발급 건을 찾아 소진하고 금액을 계산해 응답한다")
        void applies_and_replies_with_amounts() {
            IssuedCoupon issued = issue(couponId);

            couponSagaService.handle(request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT));

            IssuedCoupon after = issuedCouponRepository.findById(issued.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(after.getOrderId()).isEqualTo(ORDER_ID);

            CouponResponse response = responseOf(onlyOutbox());
            assertThat(response.couponStatus()).isEqualTo(CouponStatus.APPLIED);
            // 요청은 couponId만 실어 왔다. 무엇이 소진됐는지는 응답이 알려준다.
            assertThat(response.issuedCouponId()).isEqualTo(issued.getId());
            assertThat(response.discountAmount()).isEqualTo(10_000L);
            assertThat(response.finalAmount()).isEqualTo(90_000L);
            assertThat(response.failureMessages()).isEmpty();
        }

        @Test
        @DisplayName("할인액과 최종금액을 더하면 주문금액이 된다 — 주문 이력이 어긋나지 않는다")
        void amounts_are_consistent() {
            issue(couponId);

            couponSagaService.handle(request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT));

            CouponResponse response = responseOf(onlyOutbox());
            assertThat(response.discountAmount() + response.finalAmount()).isEqualTo(ORDER_AMOUNT);
        }

        @Test
        @DisplayName("발급이 아직 반영되지 않았으면 COUPON_NOT_ISSUED_YET으로 답한다")
        void not_issued_yet() {
            // 발급 API의 201은 Redis 수량 확보까지만 뜻한다. row는 컨슈머가 나중에 만든다.
            assertThatCode(() ->
                    couponSagaService.handle(request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT)))
                    .doesNotThrowAnyException();

            CouponResponse response = responseOf(onlyOutbox());
            assertThat(response.couponStatus()).isEqualTo(CouponStatus.FAILED);
            assertThat(response.issuedCouponId()).isNull();
            assertThat(response.failureMessages())
                    .containsExactly(ErrorCode.COUPON_NOT_ISSUED_YET.getCode());
        }

        @Test
        @DisplayName("이미 사용된 쿠폰은 예외가 아니라 실패 응답이 된다")
        void already_used_becomes_failure_response() {
            IssuedCoupon issued = issue(couponId);
            couponSagaService.handle(request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT));
            orderOutboxRepository.deleteAllInBatch();

            // 다른 주문이 같은 쿠폰을 쓰려 한다
            CouponRequest other = new CouponRequest(UUID.randomUUID(), UUID.randomUUID(), 999L,
                    USER_ID, couponId, ORDER_AMOUNT, CouponOrderStatus.PENDING, Instant.now());
            assertThatCode(() -> couponSagaService.handle(other)).doesNotThrowAnyException();

            CouponResponse response = responseOf(onlyOutbox());
            assertThat(response.couponStatus()).isEqualTo(CouponStatus.FAILED);
            assertThat(response.failureMessages())
                    .containsExactly(ErrorCode.COUPON_ALREADY_USED.getCode());
            // 원래 주인은 그대로다
            assertThat(issuedCouponRepository.findById(issued.getId()).orElseThrow().getOrderId())
                    .isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("최소 주문 금액 미달은 조용한 할인 0이 아니라 명시적 실패다")
        void min_order_amount_not_met_fails_explicitly() {
            Long gatedCouponId = saveCoupon(50_000L);
            IssuedCoupon issued = issue(gatedCouponId);

            couponSagaService.handle(request(CouponOrderStatus.PENDING, gatedCouponId, 10_000L));

            CouponResponse response = responseOf(onlyOutbox());
            assertThat(response.failureMessages())
                    .containsExactly(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET.getCode());
            // 조용히 완료됐다면 사용자는 이유를 모르는데 쿠폰만 소진된다
            assertThat(issuedCouponRepository.findById(issued.getId()).orElseThrow().getStatus())
                    .isEqualTo(IssuedCouponStatus.ISSUED);
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("같은 요청을 다시 받아도 쿠폰이 두 번 소진되지 않는다")
        void duplicate_request_does_not_apply_twice() {
            issue(couponId);
            CouponRequest first = request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT);

            couponSagaService.handle(first);
            couponSagaService.handle(sameSaga(first, CouponOrderStatus.PENDING));

            assertThat(orderOutboxRepository.findAll()).hasSize(1);
            assertThat(responseOf(onlyOutbox()).couponStatus()).isEqualTo(CouponStatus.APPLIED);
        }

        @Test
        @DisplayName("중복 요청은 무시가 아니라 기존 응답의 재발행으로 이어진다")
        void duplicate_request_marks_existing_reply_for_republish() {
            issue(couponId);
            CouponRequest first = request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT);
            couponSagaService.handle(first);

            // 응답이 이미 나갔다고 가정한다
            UUID outboxId = onlyOutbox().getId();
            orderOutboxPublishState.markPublished(outboxId);
            assertThat(orderOutboxRepository.findById(outboxId).orElseThrow().getOutboxStatus())
                    .isEqualTo(OutboxStatus.COMPLETED);

            // 그런데도 같은 요청이 또 왔다 = 조정자가 그 응답을 받지 못했다는 뜻이다
            couponSagaService.handle(sameSaga(first, CouponOrderStatus.PENDING));

            assertThat(orderOutboxRepository.findById(outboxId).orElseThrow().getOutboxStatus())
                    .as("조용히 무시하면 사가가 응답을 영영 기다린다")
                    .isEqualTo(OutboxStatus.STARTED);
        }
    }

    @Nested
    @DisplayName("쿠폰 복구(CANCELLED)")
    class Restore {

        @Test
        @DisplayName("사용을 되돌리면 다시 쓸 수 있는 상태가 된다")
        void restores_coupon() {
            IssuedCoupon issued = issue(couponId);
            CouponRequest apply = request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT);
            couponSagaService.handle(apply);

            couponSagaService.handle(sameSaga(apply, CouponOrderStatus.CANCELLED));

            IssuedCoupon after = issuedCouponRepository.findById(issued.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
            assertThat(after.getOrderId()).isNull();
        }

        @Test
        @DisplayName("정상 응답과 보상 응답이 같은 sagaId 아래 함께 남는다")
        void normal_and_compensation_replies_coexist() {
            issue(couponId);
            CouponRequest apply = request(CouponOrderStatus.PENDING, couponId, ORDER_AMOUNT);
            couponSagaService.handle(apply);
            couponSagaService.handle(sameSaga(apply, CouponOrderStatus.CANCELLED));

            // UNIQUE(saga_id, request_status)라 두 row가 공존한다.
            // request_status를 빼면 여기서 충돌해 보상 응답이 아예 저장되지 못한다.
            assertThat(orderOutboxRepository.findAll()).hasSize(2);
            assertThat(orderOutboxHelper.findProcessed(apply.sagaId(), CouponOrderStatus.PENDING))
                    .isPresent();
            assertThat(orderOutboxHelper.findProcessed(apply.sagaId(), CouponOrderStatus.CANCELLED))
                    .isPresent();
        }

        @Test
        @DisplayName("되돌릴 쿠폰이 없어도 성공으로 답한다 — 실패로 답하면 보상이 끝나지 못한다")
        void restore_without_issued_coupon_still_succeeds() {
            couponSagaService.handle(request(CouponOrderStatus.CANCELLED, couponId, ORDER_AMOUNT));

            assertThat(responseOf(onlyOutbox()).couponStatus()).isEqualTo(CouponStatus.RESTORED);
        }
    }
}
