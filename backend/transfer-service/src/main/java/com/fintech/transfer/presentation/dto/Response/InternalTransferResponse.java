package com.fintech.transfer.presentation.dto.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferStatus;

public record InternalTransferResponse(
        UUID transferId,
        UUID correlationId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        Instant startedAt,
        Instant debitRequestedAt,
        Instant debitCompletedAt,
        Instant creditRequestedAt,
        Instant creditCompletedAt,
        Instant completedAt) {

    public static InternalTransferResponse from(Transfer transfer) {
        return new InternalTransferResponse(
                transfer.getId(),
                transfer.getCorrelationId(),
                transfer.getMoney().amount(),
                transfer.getMoney().currency(),
                transfer.getStatus(),
                transfer.getStartedAt(),
                transfer.getDebitRequestedAt(),
                transfer.getDebitCompletedAt(),
                transfer.getCreditRequestedAt(),
                transfer.getCreditCompletedAt(),
                transfer.getCompletedAt());
    }
}