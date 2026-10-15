package com.fintech.transfer.application.messaging.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DebitCompletedEvent(
        UUID eventId,
        UUID correlationId,
        UUID ledgerEntryId,
        Instant processedAt
) {
    public DebitCompletedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId must not be null");
        Objects.requireNonNull(processedAt, "processedAt must not be null");
    }
}