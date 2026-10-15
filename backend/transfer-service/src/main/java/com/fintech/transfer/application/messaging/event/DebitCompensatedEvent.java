package com.fintech.transfer.application.messaging.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DebitCompensatedEvent(
        UUID eventId,
        UUID correlationId,
        UUID reversalLedgerEntryId,
        Instant compensatedAt
) {
    public DebitCompensatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(
                reversalLedgerEntryId,
                "reversalLedgerEntryId must not be null"
        );
        Objects.requireNonNull(
                compensatedAt,
                "compensatedAt must not be null"
        );
    }
}