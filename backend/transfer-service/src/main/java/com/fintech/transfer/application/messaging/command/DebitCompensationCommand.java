package com.fintech.transfer.application.messaging.command;

import com.fintech.transfer.domain.model.Transfer;

import java.math.BigDecimal;
import java.util.Map;
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

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    private static final String COMPENSATION_REASON = "CREDIT_FAILED";

    public static DebitCompensationCommand from(Transfer transfer) {
        return new DebitCompensationCommand(
                UUID.randomUUID(),
                transfer.getCorrelationId(),
                transfer.getId(),
                UUID.randomUUID().toString(),
                transfer.getFromAccountId(),
                transfer.getMoney().amount(),
                transfer.getMoney().currency(),
                COMPENSATION_REASON
        );
    }

    public Map<String, Object> toPayload() {
        return Map.of(
                "messageId", messageId.toString(),
                "correlationId", correlationId.toString(),
                "transferId", transferId.toString(),
                "idempotencyKey", idempotencyKey,
                "accountId", accountId.toString(),
                "amount", amount,
                "currency", currency,
                "reason", reason
        );
    }
}