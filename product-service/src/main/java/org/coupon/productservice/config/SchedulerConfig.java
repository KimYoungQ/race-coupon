package org.coupon.productservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled}를 켠다.
 *
 * <p>이 한 줄이 없으면 응답 Outbox 스케줄러가 <b>조용히 돌지 않는다.</b> 빈은 정상 등록되고
 * 기동 로그에도 경고가 없어서, 재고는 깎였는데 응답이 영영 안 나가는 상태를 한참 뒤에야 알게 된다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
