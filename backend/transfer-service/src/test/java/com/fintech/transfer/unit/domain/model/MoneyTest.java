package com.fintech.transfer.unit.domain.model;

import com.fintech.transfer.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidTryCurrency() {
        Money money = new Money(
                new BigDecimal("1500.00"),
                "TRY");

        assertThat(money.amount())
                .isEqualByComparingTo("1500.00");

        assertThat(money.currency())
                .isEqualTo("TRY");
    }

    @Test
    void shouldNormalizeCurrencyToUpperCase() {
        Money money = new Money(
                new BigDecimal("100.00"),
                "try");

        assertThat(money.currency())
                .isEqualTo("TRY");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> new Money(
                BigDecimal.ZERO,
                "TRY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> new Money(
                new BigDecimal("-1.00"),
                "TRY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertThatThrownBy(() -> new Money(
                new BigDecimal("100.00"),
                "ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid ISO 4217");
    }

    @Test
    void shouldRejectNonTryCurrencyInMvp() {
        assertThatThrownBy(() -> new Money(
                new BigDecimal("100.00"),
                "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only TRY is supported in the MVP");
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Money(null, "TRY"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount must not be null");
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThatThrownBy(() -> new Money(
                new BigDecimal("100.00"),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency must not be null");
    }
}