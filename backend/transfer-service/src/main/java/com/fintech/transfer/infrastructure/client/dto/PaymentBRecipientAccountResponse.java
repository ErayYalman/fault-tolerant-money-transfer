package com.fintech.transfer.infrastructure.client.dto;

import java.util.UUID;

import com.fintech.transfer.domain.model.RecipientAccount;

public record PaymentBRecipientAccountResponse(
        UUID accountId,
        String accountNumber,
        String currency,
        String status
) {

    public RecipientAccount toDomain() {
        return new RecipientAccount(
                accountId,
                accountNumber,
                currency,
                status
        );
    }
}
