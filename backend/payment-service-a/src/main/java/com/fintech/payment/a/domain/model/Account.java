package com.fintech.payment.a.domain.model;

import com.fintech.payment.a.domain.exception.AccountNotActiveException;
import com.fintech.payment.a.domain.exception.CurrencyMismatchException;
import com.fintech.payment.a.domain.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class Account {

    private final UUID id;
    private final String accountNumber;
    private final String ownerSubject;
    private final String currency;
    private final AccountStatus status;

    /*
     * Persistence katmanından gelen version değerinin snapshot'ıdır.
     * Artışını Hibernate @Version yönetecek.
     */
    private final long version;

    private BigDecimal balance;
    private Instant updatedAt;

    private Account(
            UUID id,
            String accountNumber,
            String ownerSubject,
            BigDecimal balance,
            String currency,
            AccountStatus status,
            long version,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "accountNumber must not be blank");
        }

        this.accountNumber = accountNumber;

        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "ownerSubject must not be blank");
        }

        this.ownerSubject = ownerSubject;

        this.balance = Objects.requireNonNull(
                balance,
                "balance must not be null");

        if (balance.signum() < 0) {
            throw new IllegalArgumentException(
                    "balance must not be negative");
        }

        this.currency = normalizeCurrency(currency);
        this.status = Objects.requireNonNull(
                status,
                "status must not be null");

        if (version < 0) {
            throw new IllegalArgumentException(
                    "version must not be negative");
        }

        this.version = version;
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "updatedAt must not be null");
    }

    public static Account rehydrate(
            UUID id,
            String accountNumber,
            String ownerSubject,
            BigDecimal balance,
            String currency,
            AccountStatus status,
            long version,
            Instant updatedAt) {
        return new Account(
                id,
                accountNumber,
                ownerSubject,
                balance,
                currency,
                status,
                version,
                updatedAt);
    }

    public DebitResult debit(
            BigDecimal amount,
            String transactionCurrency,
            Instant now) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(
                transactionCurrency,
                "transactionCurrency must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than zero");
        }

        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        String normalizedTransactionCurrency = normalizeCurrency(transactionCurrency);

        if (!currency.equals(normalizedTransactionCurrency)) {
            throw new CurrencyMismatchException();
        }

        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        BigDecimal balanceBefore = balance;
        BigDecimal balanceAfter = balance.subtract(amount);

        this.balance = balanceAfter;
        this.updatedAt = now;

        return new DebitResult(balanceBefore, balanceAfter);
    }

    public CreditResult credit(
            BigDecimal amount,
            String transactionCurrency,
            Instant now) {

        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(
                transactionCurrency,
                "transactionCurrency must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than zero");
        }

        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        String normalizedTransactionCurrency =
                normalizeCurrency(transactionCurrency);

        if (!currency.equals(normalizedTransactionCurrency)) {
            throw new CurrencyMismatchException();
        }

        BigDecimal balanceBefore = balance;
        BigDecimal balanceAfter = balance.add(amount);

        this.balance = balanceAfter;
        this.updatedAt = now;

        return new CreditResult(balanceBefore, balanceAfter);
    }

    public ReversalResult reverseDebit(
            BigDecimal amount,
            String transactionCurrency,
            Instant now) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(
                transactionCurrency,
                "transactionCurrency must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than zero");
        }

        String normalizedTransactionCurrency = normalizeCurrency(transactionCurrency);

        if (!currency.equals(normalizedTransactionCurrency)) {
            throw new CurrencyMismatchException();
        }

        /*
         * Reversal, daha önce başarıyla gerçekleşmiş bir debit'i telafi eder.
         * Bu yüzden normal debit'ten farklı olarak hesap ACTIVE olma şartı
         * aranmaz. Hesabın durumu değiştirilmez.
         */
        BigDecimal balanceBefore = balance;
        BigDecimal balanceAfter = balance.add(amount);

        this.balance = balanceAfter;
        this.updatedAt = now;

        return new ReversalResult(balanceBefore, balanceAfter);
    }

    public record ReversalResult(
            BigDecimal balanceBefore,
            BigDecimal balanceAfter) {
    }

    public record CreditResult(
            BigDecimal balanceBefore,
            BigDecimal balanceAfter) {
    }

    private static String normalizeCurrency(String currency) {
        Objects.requireNonNull(currency, "currency must not be null");

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

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isOwnedBy(String ownerSubject) {
        return this.ownerSubject.equals(ownerSubject);
    }

    public record DebitResult(
            BigDecimal balanceBefore,
            BigDecimal balanceAfter) {
    }
}