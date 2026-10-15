package com.fintech.transfer.infrastructure.messaging.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerErrorConfiguration {

    @Bean
    public DefaultErrorHandler kafkaListenerErrorHandler(
            @Value("${app.kafka.consumer.retry-interval-ms:1000}") long retryIntervalMs) {
        if (retryIntervalMs <= 0) {
            throw new IllegalArgumentException(
                    "Kafka consumer retry interval must be positive");
        }

        return new DefaultErrorHandler(
                new FixedBackOff(
                        retryIntervalMs,
                        FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}