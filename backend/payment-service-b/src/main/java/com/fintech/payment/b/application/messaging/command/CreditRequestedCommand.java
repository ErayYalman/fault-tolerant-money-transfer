package com.fintech.payment.b.application.messaging.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record CreditRequestedCommand(
        UUID messageId,
        UUID correlationId,
        UUID transferId,
        String idempotencyKey,
        UUID accountId,
        BigDecimal amount,
        String currency) {
    public CreditRequestedCommand {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "idempotencyKey must not be blank");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }
}