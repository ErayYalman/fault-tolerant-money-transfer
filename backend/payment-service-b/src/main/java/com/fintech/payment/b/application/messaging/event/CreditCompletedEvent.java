package com.fintech.payment.b.application.messaging.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CreditCompletedEvent(
        UUID eventId,
        UUID correlationId,
        UUID ledgerEntryId,
        Instant processedAt
) {
    public CreditCompletedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(ledgerEntryId);
        Objects.requireNonNull(processedAt);
    }

    public Map<String, Object> toPayload() {
        return Map.of(
                "eventId", eventId.toString(),
                "correlationId", correlationId.toString(),
                "ledgerEntryId", ledgerEntryId.toString(),
                "processedAt", processedAt.toString()
        );
    }
}