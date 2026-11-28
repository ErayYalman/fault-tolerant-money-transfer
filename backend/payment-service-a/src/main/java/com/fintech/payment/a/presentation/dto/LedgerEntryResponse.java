package com.fintech.payment.a.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fintech.payment.a.domain.model.LedgerEntry;

public record LedgerEntryResponse(
        UUID transferId,
        UUID correlationId,
        UUID accountId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        @Schema(
                description = "Ledger movement type",
                allowableValues = {"DEBIT", "CREDIT", "REVERSAL"}
        )
        String entryType,
        String status,
        Instant createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.transferId(),
                entry.correlationId(),
                entry.accountId(),
                entry.amount(),
                entry.balanceBefore(),
                entry.balanceAfter(),
                entry.entryType().name(),
                entry.status().name(),
                entry.createdAt()
        );
    }
}