package com.fintech.reconciliation.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record AnomalyFeatures(
        BigDecimal amount,
        long transactionDurationMs,
        long debitProcessingTime,
        long creditProcessingTime,
        int transactionsLast10min,
        int transactionsLast1h) {

    public AnomalyFeatures {
        Objects.requireNonNull(amount, "amount must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (transactionDurationMs <= 0
                || debitProcessingTime < 0
                || creditProcessingTime < 0) {
            throw new IllegalArgumentException(
                    "Timing features are outside the allowed range");
        }
        if (transactionsLast10min < 1
                || transactionsLast1h < transactionsLast10min) {
            throw new IllegalArgumentException(
                    "Transaction frequency features are inconsistent");
        }
    }
}