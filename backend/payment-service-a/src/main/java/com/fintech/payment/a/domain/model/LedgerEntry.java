package com.fintech.payment.a.domain.model;

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
        Instant createdAt
) {
    public LedgerEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(transferId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(balanceBefore);
        Objects.requireNonNull(balanceAfter);
        Objects.requireNonNull(entryType);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (balanceBefore.signum() < 0 || balanceAfter.signum() < 0) {
            throw new IllegalArgumentException("ledger balances must not be negative");
        }

        if (entryType == LedgerEntryType.DEBIT
                && balanceBefore.subtract(amount).compareTo(balanceAfter) != 0) {
            throw new IllegalArgumentException(
                    "DEBIT balanceAfter must equal balanceBefore - amount"
            );
        }

        if (entryType == LedgerEntryType.REVERSAL
                && balanceBefore.add(amount).compareTo(balanceAfter) != 0) {
            throw new IllegalArgumentException(
                    "REVERSAL balanceAfter must equal balanceBefore + amount"
            );
        }
    }
}