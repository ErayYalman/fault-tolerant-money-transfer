package com.fintech.reconciliation.infrastructure.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferSnapshot(
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
}