package com.fintech.transfer.unit.domain.model;

import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    @Test
    void shouldCreateTransferInPendingState() {
        UUID transferId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();

        Transfer transfer = Transfer.create(
                transferId,
                correlationId,
                "test-key",
                fromAccountId,
                toAccountId,
                new Money(
                        new BigDecimal("1500.00"),
                        "TRY"),
                NOW);

        assertThat(transfer.getId())
                .isEqualTo(transferId);

        assertThat(transfer.getCorrelationId())
                .isEqualTo(correlationId);

        assertThat(transfer.getIdempotencyKey())
                .isEqualTo("test-key");

        assertThat(transfer.getFromAccountId())
                .isEqualTo(fromAccountId);

        assertThat(transfer.getToAccountId())
                .isEqualTo(toAccountId);

        assertThat(transfer.getMoney().amount())
                .isEqualByComparingTo("1500.00");

        assertThat(transfer.getMoney().currency())
                .isEqualTo("TRY");

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.PENDING);

        assertThat(transfer.getStartedAt())
                .isEqualTo(NOW);

        assertThat(transfer.getCreatedAt())
                .isEqualTo(NOW);

        assertThat(transfer.getUpdatedAt())
                .isEqualTo(NOW);

        assertThat(transfer.getFailureReason())
                .isNull();
    }

    @Test
    void shouldRejectSameSourceAndTargetAccount() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> Transfer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-key",
                accountId,
                accountId,
                new Money(
                        new BigDecimal("100.00"),
                        "TRY"),
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "fromAccountId and toAccountId must be different");
    }

    @Test
    void shouldRehydrateTransferFromPersistenceData() {
        UUID transferId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();

        Transfer transfer = Transfer.rehydrate(
                transferId,
                correlationId,
                "rehydrate-key",
                fromAccountId,
                toAccountId,
                new Money(
                        new BigDecimal("500.00"),
                        "TRY"),
                TransferStatus.PENDING,
                null,
                NOW,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW);

        assertThat(transfer.getId())
                .isEqualTo(transferId);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.PENDING);

        assertThat(transfer.getMoney().amount())
                .isEqualByComparingTo("500.00");
    }
}