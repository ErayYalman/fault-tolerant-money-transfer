package com.fintech.transfer.application.messaging.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CreditCompletedEvent(
        UUID eventId,
        UUID correlationId,
        UUID ledgerEntryId,
        Instant processedAt
) {
    public CreditCompletedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId must not be null");
        Objects.requireNonNull(processedAt, "processedAt must not be null");
    }
}