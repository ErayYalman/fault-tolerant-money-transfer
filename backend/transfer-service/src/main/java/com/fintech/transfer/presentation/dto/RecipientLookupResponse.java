package com.fintech.transfer.presentation.dto;

import com.fintech.transfer.domain.model.RecipientAccount;

public record RecipientLookupResponse(
        String accountNumber,
        String currency,
        String status
) {

    public static RecipientLookupResponse from(RecipientAccount account) {
        return new RecipientLookupResponse(
                account.accountNumber(),
                account.currency(),
                account.status()
        );
    }
}
