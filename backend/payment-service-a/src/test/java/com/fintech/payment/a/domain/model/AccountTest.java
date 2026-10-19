package com.fintech.payment.a.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fintech.payment.a.domain.exception.AccountNotActiveException;
import com.fintech.payment.a.domain.exception.CurrencyMismatchException;
import com.fintech.payment.a.domain.exception.InsufficientFundsException;

class AccountTest {

        private static final Instant CREATED_AT = Instant.parse("2026-10-19T08:00:00Z");

        private static final Instant DEBIT_AT = Instant.parse("2026-10-19T08:00:05Z");

        private Account account(
                        BigDecimal balance,
                        AccountStatus status) {
                return Account.rehydrate(
                                UUID.randomUUID(),
                                "BANK-A-001",
                                balance,
                                "TRY",
                                status,
                                0L,
                                CREATED_AT);
        }

        @Test
        void shouldDebitBalanceAndReturnBeforeAndAfterValues() {
                Account account = account(
                                new BigDecimal("100.00"),
                                AccountStatus.ACTIVE);

                Account.DebitResult result = account.debit(
                                new BigDecimal("25.00"),
                                "TRY",
                                DEBIT_AT);

                assertThat(result.balanceBefore())
                                .isEqualByComparingTo("100.00");
                assertThat(result.balanceAfter())
                                .isEqualByComparingTo("75.00");
                assertThat(account.getBalance())
                                .isEqualByComparingTo("75.00");
                assertThat(account.getUpdatedAt())
                                .isEqualTo(DEBIT_AT);
        }

        @Test
        void shouldRejectDebitWhenBalanceIsInsufficient() {
                Account account = account(
                                new BigDecimal("10.00"),
                                AccountStatus.ACTIVE);

                assertThatThrownBy(() -> account.debit(
                                new BigDecimal("20.00"),
                                "TRY",
                                DEBIT_AT)).isInstanceOf(InsufficientFundsException.class);

                assertThat(account.getBalance())
                                .isEqualByComparingTo("10.00");
                assertThat(account.getUpdatedAt())
                                .isEqualTo(CREATED_AT);
        }

        @Test
        void shouldRejectDebitWhenAccountIsNotActive() {
                Account account = account(
                                new BigDecimal("100.00"),
                                AccountStatus.FROZEN);

                assertThatThrownBy(() -> account.debit(
                                new BigDecimal("10.00"),
                                "TRY",
                                DEBIT_AT)).isInstanceOf(AccountNotActiveException.class);

                assertThat(account.getBalance())
                                .isEqualByComparingTo("100.00");
        }

        @Test
        void shouldRejectDebitWhenCurrencyDoesNotMatch() {
                Account account = account(
                                new BigDecimal("100.00"),
                                AccountStatus.ACTIVE);

                assertThatThrownBy(() -> account.debit(
                                new BigDecimal("10.00"),
                                "USD",
                                DEBIT_AT)).isInstanceOf(CurrencyMismatchException.class);

                assertThat(account.getBalance())
                                .isEqualByComparingTo("100.00");
        }

        @Test
        void shouldRejectNonPositiveDebitAmount() {
                Account account = account(
                                new BigDecimal("100.00"),
                                AccountStatus.ACTIVE);

                assertThatThrownBy(() -> account.debit(
                                BigDecimal.ZERO,
                                "TRY",
                                DEBIT_AT)).isInstanceOf(IllegalArgumentException.class);

                assertThat(account.getBalance())
                                .isEqualByComparingTo("100.00");
        }

        @Test
        void shouldReverseDebitEvenWhenAccountIsFrozen() {
                Account account = account(
                                new BigDecimal("75.00"),
                                AccountStatus.FROZEN);

                Account.ReversalResult result = account.reverseDebit(
                                new BigDecimal("25.00"),
                                "TRY",
                                DEBIT_AT);

                assertThat(result.balanceBefore())
                                .isEqualByComparingTo("75.00");
                assertThat(result.balanceAfter())
                                .isEqualByComparingTo("100.00");
                assertThat(account.getBalance())
                                .isEqualByComparingTo("100.00");
                assertThat(account.getStatus())
                                .isEqualTo(AccountStatus.FROZEN);
        }
}