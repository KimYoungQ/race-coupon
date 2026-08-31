package org.coupon.orderservice.config;

import org.apache.kafka.common.TopicPartition;
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

@Configuration
public class SagaKafkaConfig {

    @Value("${spring.kafka.listener.concurrency:3}")
    private int concurrency;

    @Value("${order-service.saga.retry.interval-ms:1000}")
    private long retryIntervalMs;

    @Value("${order-service.saga.retry.max-attempts:3}")
    private long retryMaxAttempts;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> sagaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);

        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        factory.setCommonErrorHandler(sagaErrorHandler(kafkaTemplate));
        return factory;
    }

    private DefaultErrorHandler sagaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryMaxAttempts));
    }
}
