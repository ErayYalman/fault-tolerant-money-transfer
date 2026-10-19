package com.fintech.payment.a.infrastructure.messaging.kafka;

import org.springframework.stereotype.Component;

@Component
public class PaymentKafkaTopicResolver {

    public String resolve(String eventType) {
        return switch (eventType) {
            case "debit-completed" -> PaymentKafkaTopics.DEBIT_COMPLETED;
            case "debit-failed" -> PaymentKafkaTopics.DEBIT_FAILED;
            default -> throw new IllegalArgumentException(
                    "Unsupported payment outbox event type: " + eventType
            );
        };
    }
}