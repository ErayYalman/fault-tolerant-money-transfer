package com.fintech.transfer.domain.port.in;

import com.fintech.transfer.domain.model.Transfer;

import java.math.BigDecimal;
import java.util.UUID;

public interface InitiateTransferUseCase {

    CreateTransferResult execute(InitiateTransferCommand command);

    record InitiateTransferCommand(
            BigDecimal amount,
            String currency,
            UUID fromAccountId,
            UUID toAccountId,
            String idempotencyKey
    ) {
    }

    record CreateTransferResult(
            Transfer transfer,
            boolean created
    ) {
    }
}