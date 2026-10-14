package com.fintech.transfer.infrastructure.config;

import com.fintech.transfer.infrastructure.messaging.kafka.TransferKafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KafkaTopicConfiguration {

    @Bean
    public KafkaAdmin.NewTopics transferTopics(
            @Value("${app.kafka.topic-partitions:3}") int partitions
    ) {
        if (partitions < 1) {
            throw new IllegalArgumentException(
                    "Kafka topic partition count must be positive"
            );
        }

        short replicationFactor = 1;

        return new KafkaAdmin.NewTopics(
                new NewTopic(
                        TransferKafkaTopics.DEBIT_REQUESTED,
                        partitions,
                        replicationFactor
                ),
                new NewTopic(
                        TransferKafkaTopics.CREDIT_REQUESTED,
                        partitions,
                        replicationFactor
                ),
                new NewTopic(
                        TransferKafkaTopics.DEBIT_COMPENSATE,
                        partitions,
                        replicationFactor
                ),
                new NewTopic(
                        TransferKafkaTopics.DEBIT_COMPLETED,
                        partitions,
                        replicationFactor
                ),
                new NewTopic(
                        TransferKafkaTopics.DEBIT_FAILED,
                        partitions,
                        replicationFactor
                ),
                new NewTopic(
                        TransferKafkaTopics.CREDIT_COMPLETED,
                        partitions,
                        replicationFactor
                ),
                new NewTopic(
                        TransferKafkaTopics.CREDIT_FAILED,
                        partitions,
                        replicationFactor
                ),
                new NewTopic(
                        TransferKafkaTopics.DEBIT_COMPENSATED,
                        partitions,
                        replicationFactor
                )
        );
    }
}