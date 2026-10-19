package com.fintech.payment.a.application.messaging.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DebitCompensatedEvent(
        UUID eventId,
        UUID correlationId,
        UUID reversalLedgerEntryId,
        Instant compensatedAt
) {
    public DebitCompensatedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(reversalLedgerEntryId);
        Objects.requireNonNull(compensatedAt);
    }

    public Map<String, Object> toPayload() {
        return Map.of(
                "eventId", eventId.toString(),
                "correlationId", correlationId.toString(),
                "reversalLedgerEntryId", reversalLedgerEntryId.toString(),
                "compensatedAt", compensatedAt.toString()
        );
    }
}