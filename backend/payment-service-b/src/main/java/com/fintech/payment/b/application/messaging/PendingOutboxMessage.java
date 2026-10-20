package com.fintech.payment.b.application.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PendingOutboxMessage(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload,
        Instant createdAt) {
    public PendingOutboxMessage {
        Objects.requireNonNull(id);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(createdAt);

        payload = Map.copyOf(payload);
    }
}