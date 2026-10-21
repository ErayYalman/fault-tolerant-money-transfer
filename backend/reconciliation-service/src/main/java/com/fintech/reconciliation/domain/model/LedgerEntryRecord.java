package com.fintech.reconciliation.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntryRecord(
        UUID transferId,
        UUID correlationId,
        UUID accountId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String entryType,
        String status,
        Instant createdAt) {

    public LedgerEntryRecord {
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceBefore, "balanceBefore must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}