package com.fintech.transfer.domain.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(
        BigDecimal amount,
        String currency
) {

    private static final String MVP_CURRENCY = "TRY";

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        String normalizedCurrency = currency.toUpperCase(Locale.ROOT);

        try {
            Currency.getInstance(normalizedCurrency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "currency must be a valid ISO 4217 currency code",
                    exception
            );
        }

        if (!MVP_CURRENCY.equals(normalizedCurrency)) {
            throw new IllegalArgumentException(
                    "Only TRY is supported in the MVP"
            );
        }

        currency = normalizedCurrency;
    }
}