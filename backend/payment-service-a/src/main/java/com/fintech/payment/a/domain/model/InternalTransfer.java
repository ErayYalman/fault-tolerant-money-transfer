package com.fintech.payment.a.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record InternalTransfer(
                UUID id,
                UUID correlationId,
                String ownerSubject,
                UUID fromAccountId,
                UUID toAccountId,
                BigDecimal amount,
                String currency,
                String idempotencyKey,
                String requestFingerprint,
                Instant createdAt) {

        public InternalTransfer {
                Objects.requireNonNull(id, "id must not be null");
                Objects.requireNonNull(
                                correlationId,
                                "correlationId must not be null");

                if (ownerSubject == null || ownerSubject.isBlank()
                                || ownerSubject.length() > 255) {
                        throw new IllegalArgumentException(
                                        "ownerSubject must contain between 1 and 255 characters");
                }

                Objects.requireNonNull(
                                fromAccountId,
                                "fromAccountId must not be null");
                Objects.requireNonNull(
                                toAccountId,
                                "toAccountId must not be null");

                if (fromAccountId.equals(toAccountId)) {
                        throw new IllegalArgumentException(
                                        "Source and destination accounts must be different");
                }

                amount = normalizeAmount(amount);

                currency = normalizeCurrency(currency);

                if (idempotencyKey == null
                                || idempotencyKey.isBlank()
                                || idempotencyKey.length() > 100) {
                        throw new IllegalArgumentException(
                                        "idempotencyKey must contain between 1 and 100 characters");
                }

                if (requestFingerprint == null
                                || !requestFingerprint.matches("[0-9a-f]{64}")) {
                        throw new IllegalArgumentException(
                                        "requestFingerprint must be a lowercase SHA-256 hash");
                }

                Objects.requireNonNull(
                                createdAt,
                                "createdAt must not be null");
        }

        private static BigDecimal normalizeAmount(BigDecimal amount) {
                Objects.requireNonNull(amount, "amount must not be null");

                if (amount.signum() <= 0) {
                        throw new IllegalArgumentException(
                                        "amount must be greater than zero");
                }

                try {
                        return amount.setScale(4, RoundingMode.UNNECESSARY);
                } catch (ArithmeticException exception) {
                        throw new IllegalArgumentException(
                                        "amount must have at most 4 decimal places",
                                        exception);
                }
        }

        private static String normalizeCurrency(String currency) {
                Objects.requireNonNull(
                                currency,
                                "currency must not be null");

                String normalized = currency.strip().toUpperCase(Locale.ROOT);

                try {
                        Currency.getInstance(normalized);
                } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException(
                                        "currency must be a valid ISO 4217 currency code",
                                        exception);
                }

                return normalized;
        }
}