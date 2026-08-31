package org.coupon.orderservice.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.coupon.orderservice.domain.outbox.CouponOutbox;
import org.coupon.orderservice.domain.outbox.ProductOutbox;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
