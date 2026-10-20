package com.fintech.payment.b.application.messaging.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CreditFailedEvent(
        UUID eventId,
        UUID correlationId,
        String failureReason,
        Instant failedAt) {
    public CreditFailedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(failureReason);
        Objects.requireNonNull(failedAt);

        if (failureReason.isBlank()) {
            throw new IllegalArgumentException(
                    "failureReason must not be blank");
        }
    }

    public Map<String, Object> toPayload() {
        return Map.of(
                "eventId", eventId.toString(),
                "correlationId", correlationId.toString(),
                "failureReason", failureReason,
                "failedAt", failedAt.toString());
    }
}