package org.coupon.productservice.config;

import org.apache.kafka.common.TopicPartition;
import org.coupon.common.event.StockRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Saga 리스너 전용 컨테이너 팩토리.
 *
 * <p>전역 {@code spring.kafka.listener} 설정을 batch로 바꾸지 않고 팩토리를 따로 만든다.
 * 그 설정은 기본 팩토리를 통해 다른 리스너에도 적용되는데, 단건+수동 ack로 쓰는 리스너가
 * 하나라도 있으면 그쪽이 깨지기 때문이다. 여기서는 사가 리스너만 batch로 격리한다.
 *
 * <p><b>{@code ConsumerFactory}·{@code KafkaTemplate} 빈은 새로 선언하지 않는다.</b>
 * Boot의 {@code KafkaAutoConfiguration}이 그 둘을 {@code @ConditionalOnMissingBean(<타입>)}으로
 * 정의한다 — 이름이 아니라 <b>타입</b> 기준이라 새로 올리면 기본 빈이 사라지고 다른 프로듀서·컨슈머가
 * 함께 깨진다. 반면 {@code ConcurrentKafkaListenerContainerFactory}는
 * {@code @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")}라
 * <b>다른 이름이면 안전하게 추가된다.</b>
 */
@Configuration
public class SagaKafkaConfig {

    /** 전역 리스너 동시성을 그대로 따른다. 파티션 수와 맞춰 두지 않으면 스레드가 논다. */
    @Value("${spring.kafka.listener.concurrency:3}")
    private int concurrency;

    /** 재시도 간격·횟수. 일시적 장애는 이 안에서 지나가고, 그래도 안 되면 DLT로 간다. */
    @Value("${product-service.saga.retry.interval-ms:1000}")
    private long retryIntervalMs;

    @Value("${product-service.saga.retry.max-attempts:3}")
    private long retryMaxAttempts;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockRequest> sagaListenerContainerFactory(
            ConsumerFactory<String, StockRequest> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, StockRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);

        // 배치로 받고, 리스너가 정상 반환하면 컨테이너가 배치 오프셋을 커밋한다.
        // 코드가 커밋 시점을 제어하지 않는 것이 요점이다 — 수동 ack는 저장 실패까지 커밋해버린다.
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        factory.setCommonErrorHandler(sagaErrorHandler(kafkaTemplate));
        return factory;
    }

    /**
     * 재시도해도 안 되는 레코드를 {@code product-request.DLT}로 보낸다.
     *
     * <p><b>빈으로 노출하지 않는다.</b> {@code CommonErrorHandler} 타입 빈이 있으면 Boot가
     * 기본 컨테이너 팩토리에도 적용해 사가와 무관한 리스너까지 바꾼다.
     *
     * <p>이게 없으면 재시도 소진 후 기본 recoverer가 로그만 남기고 레코드를 건너뛴다 —
     * 요청이 조용히 유실되고 그 사가는 응답을 영영 기다린다.
     * 리스너가 던지는 {@code BatchListenerFailedException}의 index 덕분에
     * 배치 전체가 아니라 실패한 레코드 하나만 재시도되고 DLT로 간다.
     */
    private DefaultErrorHandler sagaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // 파티션 -1: 프로듀서가 키로 결정하게 한다. 원본 파티션 번호를 그대로 쓰면
                // DLT 토픽의 파티션 수가 더 적을 때 발행이 실패한다.
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryMaxAttempts));
    }
}
