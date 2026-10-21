package com.fintech.reconciliation.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TransferRecord(
        UUID transferId,
        UUID correlationId,
        BigDecimal amount,
        String currency,
        String status,
        Instant startedAt,
        Instant debitRequestedAt,
        Instant debitCompletedAt,
        Instant creditRequestedAt,
        Instant creditCompletedAt,
        Instant completedAt) {

    public TransferRecord {
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}