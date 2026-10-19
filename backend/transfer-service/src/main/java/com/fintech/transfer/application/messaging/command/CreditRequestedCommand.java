package com.fintech.transfer.application.messaging.command;

import com.fintech.transfer.domain.model.Transfer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CreditRequestedCommand(
        UUID messageId,
        UUID correlationId,
        UUID transferId,
        String idempotencyKey,
        UUID accountId,
        BigDecimal amount,
        String currency
) {
    public CreditRequestedCommand {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(transferId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static CreditRequestedCommand from(Transfer transfer) {
        return new CreditRequestedCommand(
                UUID.randomUUID(),
                transfer.getCorrelationId(),
                transfer.getId(),
                UUID.randomUUID().toString(),
                transfer.getToAccountId(),
                transfer.getMoney().amount(),
                transfer.getMoney().currency()
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
                "currency", currency
        );
    }
}