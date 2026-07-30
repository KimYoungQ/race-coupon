package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 끝난 사가의 Outbox row를 지운다.
 *
 * <p>Outbox는 "아직 못 보낸 메시지"를 담는 곳이지 이력 테이블이 아니다. 안 지우면
 * 폴링 인덱스가 계속 자라 재발행 조회가 느려진다.
 *
 * <p><b>실습 중에는 이 스케줄러를 꺼두는 편이 낫다.</b> 사가가 어떤 상태를 밟아 왔는지
 * SQL로 되짚어 보려는데 종료 즉시 지워지면 아무것도 남지 않는다.
 * {@code order-service.outbox.cleaner.cron}을 먼 시각으로 미루면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanerScheduler {

    private final ProductOutboxHelper productOutboxHelper;
    private final CouponOutboxHelper couponOutboxHelper;

    @Transactional
    @Scheduled(cron = "${order-service.outbox.cleaner.cron:0 0 4 * * *}")
    public void clean() {
        List<ProductOutbox> products = productOutboxHelper.findTerminal();
        List<CouponOutbox> coupons = couponOutboxHelper.findTerminal();

        if (products.isEmpty() && coupons.isEmpty()) {
            return;
        }

        productOutboxHelper.delete(products);
        couponOutboxHelper.delete(coupons);
        log.info("종료된 사가 Outbox 정리: product={}건, coupon={}건", products.size(), coupons.size());
    }
}
