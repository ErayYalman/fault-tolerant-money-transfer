package com.fintech.transfer.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Transfer {

    private final UUID id;
    private final UUID correlationId;
    private final String idempotencyKey;

    private final UUID fromAccountId;
    private final UUID toAccountId;

    private final Money money;

    private TransferStatus status;
    private String failureReason;

    private Instant startedAt;
    private Instant debitRequestedAt;
    private Instant debitCompletedAt;
    private Instant creditRequestedAt;
    private Instant creditCompletedAt;
    private Instant completedAt;

    private final Instant createdAt;
    private Instant updatedAt;

    private Transfer(
            UUID id,
            UUID correlationId,
            String idempotencyKey,
            UUID fromAccountId,
            UUID toAccountId,
            Money money,
            TransferStatus status,
            String failureReason,
            Instant startedAt,
            Instant debitRequestedAt,
            Instant debitCompletedAt,
            Instant creditRequestedAt,
            Instant creditCompletedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.correlationId = Objects.requireNonNull(correlationId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.fromAccountId = Objects.requireNonNull(fromAccountId);
        this.toAccountId = Objects.requireNonNull(toAccountId);
        this.money = Objects.requireNonNull(money);
        this.status = Objects.requireNonNull(status);
        this.startedAt = startedAt;
        this.debitRequestedAt = debitRequestedAt;
        this.debitCompletedAt = debitCompletedAt;
        this.creditRequestedAt = creditRequestedAt;
        this.creditCompletedAt = creditCompletedAt;
        this.completedAt = completedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.failureReason = failureReason;

        validateAccountRelationship();
    }

    public static Transfer create(
            UUID id,
            UUID correlationId,
            String idempotencyKey,
            UUID fromAccountId,
            UUID toAccountId,
            Money money,
            Instant now) {
        Objects.requireNonNull(now);

        return new Transfer(
                id,
                correlationId,
                idempotencyKey,
                fromAccountId,
                toAccountId,
                money,
                TransferStatus.PENDING,
                null,
                now,
                null,
                null,
                null,
                null,
                null,
                now,
                now);
    }

    public static Transfer rehydrate(
            UUID id,
            UUID correlationId,
            String idempotencyKey,
            UUID fromAccountId,
            UUID toAccountId,
            Money money,
            TransferStatus status,
            String failureReason,
            Instant startedAt,
            Instant debitRequestedAt,
            Instant debitCompletedAt,
            Instant creditRequestedAt,
            Instant creditCompletedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {
        return new Transfer(
                id,
                correlationId,
                idempotencyKey,
                fromAccountId,
                toAccountId,
                money,
                status,
                failureReason,
                startedAt,
                debitRequestedAt,
                debitCompletedAt,
                creditRequestedAt,
                creditCompletedAt,
                completedAt,
                createdAt,
                updatedAt);
    }

    private void validateAccountRelationship() {
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException(
                    "fromAccountId and toAccountId must be different");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public Money getMoney() {
        return money;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDebitRequestedAt() {
        return debitRequestedAt;
    }

    public Instant getDebitCompletedAt() {
        return debitCompletedAt;
    }

    public Instant getCreditRequestedAt() {
        return creditRequestedAt;
    }

    public Instant getCreditCompletedAt() {
        return creditCompletedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}