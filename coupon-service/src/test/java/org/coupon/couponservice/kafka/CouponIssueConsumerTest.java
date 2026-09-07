package org.coupon.couponservice.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.coupon.couponservice.domain.Coupon;
import org.coupon.couponservice.domain.DiscountType;
import org.coupon.couponservice.repository.CouponRepository;
import org.coupon.couponservice.repository.IssuedCouponRepository;
import org.coupon.couponservice.support.MySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainer.class)
class CouponIssueConsumerTest {

    private static final long USER_ID = 42L;

    @Autowired
    private CouponIssueConsumer couponIssueConsumer;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("발급 메시지를 받으면 IssuedCoupon 이 저장되고 발급 수량이 늘어난다")
    void consumeSavesIssuedCoupon() {
        // given
        Long couponId = saveCoupon();

        // when
        couponIssueConsumer.consume(record(couponId), noAck());

        // then
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L);
        assertThat(couponRepository.findById(couponId).orElseThrow().getIssuedQuantity()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 발급 메시지를 두 번 받아도 IssuedCoupon 은 한 건만 남는다")
    void duplicateMessageLeavesOneIssuedCoupon() {
        // given
        Long couponId = saveCoupon();
        couponIssueConsumer.consume(record(couponId), noAck());

        // when
        couponIssueConsumer.consume(record(couponId), noAck());

        // then
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L);
        assertThat(couponRepository.findById(couponId).orElseThrow().getIssuedQuantity()).isEqualTo(1L);
    }

    private Long saveCoupon() {
        return couponRepository.save(Coupon.builder()
                .title("선착순 쿠폰")
                .totalQuantity(100L)
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .eventEndAt(LocalDateTime.now().plusDays(1))
                .build()).getId();
    }

    private ConsumerRecord<String, CouponIssueMessage> record(Long couponId) {
        return new ConsumerRecord<>(CouponIssueMessage.TOPIC, 0, 0L,
                String.valueOf(USER_ID), new CouponIssueMessage(couponId, USER_ID));
    }

    private Acknowledgment noAck() {
        return () -> {
        };
    }
}
