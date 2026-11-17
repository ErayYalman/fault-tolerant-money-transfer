package com.fintech.payment.a.presentation.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fintech.payment.a.domain.model.Account;

public record AccountSummaryResponse(
        UUID accountId,
        String accountNumber,
        BigDecimal balance,
        String currency,
        String status
) {
    public static AccountSummaryResponse from(Account account) {
        return new AccountSummaryResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus().name()
        );
    }
}
