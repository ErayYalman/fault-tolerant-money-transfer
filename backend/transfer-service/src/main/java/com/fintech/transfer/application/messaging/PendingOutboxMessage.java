package com.fintech.transfer.application.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PendingOutboxMessage(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload,
        Instant createdAt) {

    public PendingOutboxMessage {
        payload = Map.copyOf(payload);
    }
}