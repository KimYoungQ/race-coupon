package org.coupon.orderservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled}를 켠다.
 *
 * <p>이 한 줄이 없으면 Outbox 재발행 스케줄러가 <b>조용히 돌지 않는다.</b> 빈은 정상적으로 등록되고
 * 기동 로그에도 아무 경고가 없어서, "즉시 발행이 되니까 잘 도는구나" 하고 넘어가기 쉽다.
 * 문제는 프로세스가 죽었다 살아난 뒤에야 드러난다 — 그때는 재발행할 사람이 아무도 없다.
 *
 * <p>스레드 풀 크기는 {@code spring.task.scheduling.pool.size}로 키워 둔다(기본 1).
 * 재발행 2개 + Cleaner가 한 스레드에 몰리면 서로를 막는다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
