package com.fintech.transfer.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RecipientAccount(
        UUID accountId,
        String accountNumber,
        String currency,
        String status
) {
    public RecipientAccount {
        Objects.requireNonNull(accountId, "accountId must not be null");

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "accountNumber must not be blank");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "currency must not be blank");
        }

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "status must not be blank");
        }
    }
}
