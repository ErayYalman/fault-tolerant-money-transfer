package com.fintech.payment.b.application.messaging;

import java.util.Objects;
import java.util.UUID;

public record ConsumedCommand(
        UUID messageId,
        String idempotencyKey,
        UUID transferId,
        UUID correlationId,
        String commandType,
        String requestHash) {
    public ConsumedCommand {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(transferId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(commandType);
        Objects.requireNonNull(requestHash);

        if (idempotencyKey.isBlank() || commandType.isBlank()) {
            throw new IllegalArgumentException(
                    "idempotencyKey and commandType must not be blank");
        }

        if (!requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "requestHash must be a SHA-256 hex value");
        }
    }
}