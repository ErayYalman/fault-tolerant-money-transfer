package com.fintech.transfer.presentation.dto.Response;

import com.fintech.transfer.domain.model.TransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Transfer response")
public record TransferResponse(

        UUID id,
        UUID correlationId,
        String idempotencyKey,

        UUID fromAccountId,
        UUID toAccountId,

        BigDecimal amount,
        String currency,

        TransferStatus status,
        String failureReason,

        Instant startedAt,
        Instant debitRequestedAt,
        Instant debitCompletedAt,
        Instant creditRequestedAt,
        Instant creditCompletedAt,
        Instant completedAt,

        Instant createdAt,
        Instant updatedAt
) {
}