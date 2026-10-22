package com.fintech.reconciliation.application.service;

import java.util.UUID;

public class ReconciliationExecutionException extends RuntimeException {

    private final UUID runId;

    public ReconciliationExecutionException(
            UUID runId,
            Throwable cause) {
        super("Reconciliation execution failed", cause);
        this.runId = runId;
    }

    public UUID getRunId() {
        return runId;
    }
}