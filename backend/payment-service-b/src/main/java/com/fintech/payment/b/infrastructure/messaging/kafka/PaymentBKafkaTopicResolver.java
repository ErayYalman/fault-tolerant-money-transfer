package com.fintech.payment.b.infrastructure.messaging.kafka;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PaymentBKafkaTopicResolver {

    public String resolve(String eventType) {
        return switch (eventType) {
            case "credit-completed" ->
                PaymentBKafkaTopics.CREDIT_COMPLETED;

            case "credit-failed" ->
                PaymentBKafkaTopics.CREDIT_FAILED;

            default -> throw new IllegalArgumentException(
                    "Unsupported payment outbox event type: " + eventType);
        };
    }
}