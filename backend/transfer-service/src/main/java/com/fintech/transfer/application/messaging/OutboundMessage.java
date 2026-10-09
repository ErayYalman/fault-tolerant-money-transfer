package com.fintech.transfer.application.messaging;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OutboundMessage(
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload) {

    public OutboundMessage {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        if (aggregateType.isBlank()) {
            throw new IllegalArgumentException(
                    "aggregateType must not be blank");
        }

        if (eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "eventType must not be blank");
        }

        payload = Map.copyOf(payload);
    }
}