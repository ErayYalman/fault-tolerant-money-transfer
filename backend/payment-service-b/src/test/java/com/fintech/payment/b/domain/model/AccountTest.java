package com.fintech.payment.b.domain.model;

import com.fintech.payment.b.domain.exception.AccountNotActiveException;
import com.fintech.payment.b.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    private static final Instant INITIAL_AT =
            Instant.parse("2026-10-20T09:00:00Z");

    private static final Instant CREDIT_AT =
            Instant.parse("2026-10-20T09:00:05Z");

    private Account account(
            BigDecimal balance,
            AccountStatus status
    ) {
        return Account.rehydrate(
                UUID.randomUUID(),
                "BANK-B-001",
                balance,
                "TRY",
                status,
                0L,
                INITIAL_AT
        );
    }

    @Test
    void shouldCreditBalanceAndReturnBeforeAndAfterValues() {
        Account account = account(
                new BigDecimal("75.00"),
                AccountStatus.ACTIVE
        );

        Account.CreditResult result = account.credit(
                new BigDecimal("25.00"),
                "TRY",
                CREDIT_AT
        );

        assertMoney("75.00", result.balanceBefore());
        assertMoney("100.00", result.balanceAfter());
        assertMoney("100.00", account.getBalance());
        assertEquals(CREDIT_AT, account.getUpdatedAt());
    }

    @Test
    void shouldRejectCreditWhenAccountIsFrozen() {
        Account account = account(
                new BigDecimal("75.00"),
                AccountStatus.FROZEN
        );

        assertThrows(
                AccountNotActiveException.class,
                () -> account.credit(
                        new BigDecimal("25.00"),
                        "TRY",
                        CREDIT_AT
                )
        );

        assertMoney("75.00", account.getBalance());
        assertEquals(INITIAL_AT, account.getUpdatedAt());
    }

    @Test
    void shouldRejectCreditWhenCurrencyDoesNotMatch() {
        Account account = account(
                new BigDecimal("75.00"),
                AccountStatus.ACTIVE
        );

        assertThrows(
                CurrencyMismatchException.class,
                () -> account.credit(
                        new BigDecimal("25.00"),
                        "USD",
                        CREDIT_AT
                )
        );

        assertMoney("75.00", account.getBalance());
        assertEquals(INITIAL_AT, account.getUpdatedAt());
    }

    @Test
    void shouldRejectNonPositiveCreditAmount() {
        Account account = account(
                new BigDecimal("75.00"),
                AccountStatus.ACTIVE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> account.credit(
                        BigDecimal.ZERO,
                        "TRY",
                        CREDIT_AT
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> account.credit(
                        new BigDecimal("-1.00"),
                        "TRY",
                        CREDIT_AT
                )
        );

        assertMoney("75.00", account.getBalance());
        assertEquals(INITIAL_AT, account.getUpdatedAt());
    }

    @Test
    void shouldNormalizeCurrencyBeforeComparing() {
        Account account = Account.rehydrate(
                UUID.randomUUID(),
                "BANK-B-002",
                new BigDecimal("75.00"),
                "try",
                AccountStatus.ACTIVE,
                0L,
                INITIAL_AT
        );

        account.credit(
                new BigDecimal("5.00"),
                " TRY ",
                CREDIT_AT
        );

        assertEquals("TRY", account.getCurrency());
        assertMoney("80.00", account.getBalance());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Expected monetary amount: " + expected
        );
    }
}