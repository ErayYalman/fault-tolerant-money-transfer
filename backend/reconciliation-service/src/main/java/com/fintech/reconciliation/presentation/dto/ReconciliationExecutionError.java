package com.fintech.reconciliation.presentation.dto;

import java.util.UUID;

public record ReconciliationExecutionError(
        UUID runId,
        String message) {
}