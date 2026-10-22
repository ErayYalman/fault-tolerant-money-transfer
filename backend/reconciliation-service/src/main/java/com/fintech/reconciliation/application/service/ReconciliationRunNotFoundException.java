package com.fintech.reconciliation.application.service;

import java.util.UUID;

public class ReconciliationRunNotFoundException extends RuntimeException {

    public ReconciliationRunNotFoundException(UUID runId) {
        super("Reconciliation run not found: " + runId);
    }
}