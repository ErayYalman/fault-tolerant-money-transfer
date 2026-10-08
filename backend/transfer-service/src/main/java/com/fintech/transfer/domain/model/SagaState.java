package com.fintech.transfer.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SagaState {

    private final UUID id;
    private final UUID transferId;

    private SagaStep currentStep;
    private final Map<String, Object> payload;
    private Instant updatedAt;

    private SagaState(
            UUID id,
            UUID transferId,
            SagaStep currentStep,
            Map<String, Object> payload,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.transferId = Objects.requireNonNull(transferId);
        this.currentStep = Objects.requireNonNull(currentStep);
        this.payload = new LinkedHashMap<>(
                payload == null ? Map.of() : payload);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static SagaState start(
            UUID id,
            UUID transferId,
            Instant now) {
        return new SagaState(
                id,
                transferId,
                SagaStep.DEBIT,
                Map.of(),
                now);
    }

    public static SagaState rehydrate(
            UUID id,
            UUID transferId,
            SagaStep currentStep,
            Map<String, Object> payload,
            Instant updatedAt) {
        return new SagaState(
                id,
                transferId,
                currentStep,
                payload,
                updatedAt);
    }

    public void moveToCredit(Instant now) {
        transitionTo(SagaStep.CREDIT, now);
    }

    public void moveToCompensation(Instant now) {
        transitionTo(SagaStep.COMPENSATION, now);
    }

    public void finish(Instant now) {
        transitionTo(SagaStep.FINISHED, now);
    }

    public void putPayload(
            String key,
            Object value,
            Instant now) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(now);

        payload.put(
                Objects.requireNonNull(key),
                value);

        updatedAt = now;
    }

    private void transitionTo(
            SagaStep target,
            Instant now) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(now);

        if (!isValidTransition(target)) {
            throw new IllegalStateException(
                    "Invalid saga step transition: "
                            + currentStep
                            + " -> "
                            + target);
        }

        currentStep = target;
        updatedAt = now;
    }

    private boolean isValidTransition(SagaStep target) {
        return switch (currentStep) {
            case DEBIT ->
                target == SagaStep.CREDIT
                        || target == SagaStep.FINISHED;

            case CREDIT ->
                target == SagaStep.COMPENSATION
                        || target == SagaStep.FINISHED;

            case COMPENSATION ->
                target == SagaStep.FINISHED;

            case FINISHED ->
                false;
        };
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public SagaStep getCurrentStep() {
        return currentStep;
    }

    public Map<String, Object> getPayload() {
        return Map.copyOf(payload);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}