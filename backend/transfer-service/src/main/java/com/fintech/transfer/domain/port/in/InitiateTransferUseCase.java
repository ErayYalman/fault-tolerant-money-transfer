package com.fintech.transfer.domain.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import com.fintech.transfer.domain.model.Transfer;

public interface InitiateTransferUseCase {

        CreateTransferResult execute(InitiateTransferCommand command);

        record InitiateTransferCommand(
                        BigDecimal amount,
                        String currency,
                        UUID fromAccountId,
                        UUID toAccountId,
                        String idempotencyKey,
                        String ownerSubject) {
        }

        record CreateTransferResult(
                        Transfer transfer,
                        boolean created) {
        }
}