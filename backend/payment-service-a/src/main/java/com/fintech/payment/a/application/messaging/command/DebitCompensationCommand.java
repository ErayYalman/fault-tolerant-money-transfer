package com.fintech.payment.a.application.messaging.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record DebitCompensationCommand(
        UUID messageId,
        UUID correlationId,
        UUID transferId,
        String idempotencyKey,
        UUID accountId,
        BigDecimal amount,
        String currency,
        String reason
) {
    public DebitCompensationCommand {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(transferId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(reason);

        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "idempotencyKey must not be blank"
            );
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be positive"
            );
        }

        if (currency.isBlank()) {
            throw new IllegalArgumentException(
                    "currency must not be blank"
            );
        }

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank"
            );
        }
    }
}