package com.fintech.transfer.infrastructure.messaging.kafka;

import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {

    public String resolve(String eventType) {
        return switch (eventType) {
            case "debit-requested" ->
                    TransferKafkaTopics.DEBIT_REQUESTED;

            case "credit-requested" ->
                    TransferKafkaTopics.CREDIT_REQUESTED;

            case "debit-compensate" ->
                    TransferKafkaTopics.DEBIT_COMPENSATE;

            case "debit-completed" ->
                    TransferKafkaTopics.DEBIT_COMPLETED;

            case "debit-failed" ->
                    TransferKafkaTopics.DEBIT_FAILED;

            case "credit-completed" ->
                    TransferKafkaTopics.CREDIT_COMPLETED;

            case "credit-failed" ->
                    TransferKafkaTopics.CREDIT_FAILED;

            case "debit-compensated" ->
                    TransferKafkaTopics.DEBIT_COMPENSATED;

            default -> throw new IllegalArgumentException(
                    "Unsupported outbox message type: " + eventType
            );
        };
    }
}