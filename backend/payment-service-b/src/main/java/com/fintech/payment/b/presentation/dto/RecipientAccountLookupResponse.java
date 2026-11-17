package com.fintech.payment.b.presentation.dto;

import java.util.UUID;

import com.fintech.payment.b.domain.model.Account;

public record RecipientAccountLookupResponse(
        UUID accountId,
        String accountNumber,
        String currency,
        String status
) {

    public static RecipientAccountLookupResponse from(Account account) {
        return new RecipientAccountLookupResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getStatus().name()
        );
    }
}
