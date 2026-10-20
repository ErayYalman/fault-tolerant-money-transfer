package com.fintech.payment.b.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
        UUID id,
        UUID transferId,
        UUID correlationId,
        UUID accountId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        LedgerEntryType entryType,
        LedgerEntryStatus status,
        Instant createdAt) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceBefore, "balanceBefore must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (balanceBefore.signum() < 0 || balanceAfter.signum() < 0) {
            throw new IllegalArgumentException(
                    "ledger balances must not be negative");
        }

        if (entryType == LedgerEntryType.CREDIT
                && balanceBefore.add(amount).compareTo(balanceAfter) != 0) {
            throw new IllegalArgumentException(
                    "CREDIT balanceAfter must equal balanceBefore + amount");
        }
    }
}