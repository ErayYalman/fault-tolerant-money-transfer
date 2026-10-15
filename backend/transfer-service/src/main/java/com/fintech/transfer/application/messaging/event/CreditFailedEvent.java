package com.fintech.transfer.application.messaging.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CreditFailedEvent(
        UUID eventId,
        UUID correlationId,
        String failureReason,
        Instant failedAt
) {
    public CreditFailedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(failureReason, "failureReason must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");

        if (failureReason.isBlank()) {
            throw new IllegalArgumentException(
                    "failureReason must not be blank"
            );
        }
    }
}