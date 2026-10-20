package com.fintech.payment.b.application.messaging;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OutboundMessage(
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload) {
    public OutboundMessage {
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(payload);

        if (aggregateType.isBlank() || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "aggregateType and eventType must not be blank");
        }

        payload = Map.copyOf(payload);
    }
}