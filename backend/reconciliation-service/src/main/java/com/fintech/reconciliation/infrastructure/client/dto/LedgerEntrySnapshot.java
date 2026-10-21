package com.fintech.reconciliation.infrastructure.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntrySnapshot(
        UUID transferId,
        UUID correlationId,
        UUID accountId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String entryType,
        String status,
        Instant createdAt) {
}