package com.fintech.payment.a.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fintech.payment.a.domain.model.InternalTransfer;

public record InternalTransferResponse(
        UUID transferId,
        UUID correlationId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String status,
        boolean replayed,
        Instant createdAt) {

    public static InternalTransferResponse from(
            InternalTransfer transfer,
            boolean replayed) {

        return new InternalTransferResponse(
                transfer.id(),
                transfer.correlationId(),
                transfer.fromAccountId(),
                transfer.toAccountId(),
                transfer.amount(),
                transfer.currency(),
                "COMPLETED",
                replayed,
                transfer.createdAt());
    }
}
