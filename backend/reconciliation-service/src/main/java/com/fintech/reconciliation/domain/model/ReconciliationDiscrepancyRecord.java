package com.fintech.reconciliation.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReconciliationDiscrepancyRecord(
        UUID id,
        UUID transferId,
        UUID correlationId,
        String ruleCode,
        Map<String, String> details,
        Instant detectedAt) {
}