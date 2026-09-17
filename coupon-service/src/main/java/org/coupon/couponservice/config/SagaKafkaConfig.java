package org.coupon.couponservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.coupon.couponservice.kafka.CouponIssueMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class SagaKafkaConfig {

    @Value("${saga.listener.concurrency:3}")
    private int concurrency;

    @Value("${saga.listener.auto-startup:true}")
    private boolean autoStartup;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> sagaListenerContainerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.setConcurrency(concurrency);
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    /**
     * 쿠폰 발급과 재시도에 사용할 JSON 템플릿.
     * 전용 템플릿 추가로 Boot 자동 생성이 꺼져 직접 등록
     */
    @Bean
    @Primary
    public KafkaTemplate<String, CouponIssueMessage> kafkaTemplate(
            ProducerFactory<String, CouponIssueMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * 사가 메시지를 원문 그대로 재시도·DLT 토픽에 보내는 템플릿.
     * JSON 직렬화로 본문에 따옴표가 추가되는 것을 방지한다.
     */
    @Bean
    public KafkaTemplate<String, String> sagaRetryKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
