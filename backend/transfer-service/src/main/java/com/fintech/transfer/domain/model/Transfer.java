package com.fintech.transfer.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fintech.transfer.domain.exception.InvalidTransferStateTransitionException;

public final class Transfer {

    private final UUID id;
    private final UUID correlationId;
    private final String idempotencyKey;
    private final String ownerSubject;

    private final UUID fromAccountId;
    private final UUID toAccountId;

    private final Money money;

    private final String requestFingerprint;

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
            String ownerSubject,
            UUID fromAccountId,
            UUID toAccountId,
            Money money,
            String requestFingerprint,
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

        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "ownerSubject must not be blank");
        }
        this.ownerSubject = ownerSubject;

        this.fromAccountId = Objects.requireNonNull(fromAccountId);
        this.toAccountId = Objects.requireNonNull(toAccountId);
        this.money = Objects.requireNonNull(money);
        this.requestFingerprint = requestFingerprint;
        this.status = Objects.requireNonNull(status);

        this.failureReason = failureReason;

        this.startedAt = startedAt;
        this.debitRequestedAt = debitRequestedAt;
        this.debitCompletedAt = debitCompletedAt;
        this.creditRequestedAt = creditRequestedAt;
        this.creditCompletedAt = creditCompletedAt;
        this.completedAt = completedAt;

        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);

        validateAccountRelationship();
    }

    public static Transfer create(
            UUID id,
            UUID correlationId,
            String idempotencyKey,
            String ownerSubject,
            UUID fromAccountId,
            UUID toAccountId,
            Money money,
            String requestFingerprint,
            Instant now) {
        Objects.requireNonNull(now);

        if (requestFingerprint == null || requestFingerprint.isBlank()) {
            throw new IllegalArgumentException(
                    "requestFingerprint must not be blank"
            );
        }

        return new Transfer(
                id,
                correlationId,
                idempotencyKey,
                ownerSubject,
                fromAccountId,
                toAccountId,
                money,
                requestFingerprint,
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
            String ownerSubject,
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
            Instant updatedAt,
            String requestFingerprint) {
        return new Transfer(
                id,
                correlationId,
                idempotencyKey,
                ownerSubject,
                fromAccountId,
                toAccountId,
                money,
                requestFingerprint,
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

    public void requestDebit(Instant now) {
        transitionTo(TransferStatus.DEBIT_REQUESTED, now);
        this.debitRequestedAt = now;
    }

    public void completeDebit(Instant now) {
        transitionTo(TransferStatus.DEBIT_COMPLETED, now);
        this.debitCompletedAt = now;
    }

    public void failDebit(
            String failureReason,
            Instant now) {
        validateFailureReason(failureReason);

        transitionTo(TransferStatus.DEBIT_FAILED, now);

        this.failureReason = failureReason;
    }

    public void requestCredit(Instant now) {
        transitionTo(TransferStatus.CREDIT_REQUESTED, now);
        this.creditRequestedAt = now;
    }

    public void completeCredit(Instant now) {
        transitionTo(TransferStatus.COMPLETED, now);

        this.creditCompletedAt = now;
        this.completedAt = now;
        this.failureReason = null;
    }

    public void failCredit(
            String failureReason,
            Instant now) {
        validateFailureReason(failureReason);

        transitionTo(TransferStatus.CREDIT_FAILED, now);

        this.failureReason = failureReason;
    }

    public void startCompensation(Instant now) {
        transitionTo(TransferStatus.COMPENSATING, now);
    }

    public void completeCompensation(Instant now) {
        transitionTo(TransferStatus.COMPENSATED, now);

        this.completedAt = now;
    }

    private void transitionTo(
            TransferStatus target,
            Instant now) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(now);

        if (!status.canTransitionTo(target)) {
            throw new InvalidTransferStateTransitionException(
                    status,
                    target);
        }

        this.status = target;
        this.updatedAt = now;
    }

    private void validateFailureReason(
            String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException(
                    "failureReason must not be blank");
        }
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

    public String getOwnerSubject() {
        return ownerSubject;
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

    public String getRequestFingerprint() {
        return requestFingerprint;
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