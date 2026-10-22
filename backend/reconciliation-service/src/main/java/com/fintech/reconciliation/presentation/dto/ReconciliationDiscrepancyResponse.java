package com.fintech.reconciliation.presentation.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancyRecord;

public record ReconciliationDiscrepancyResponse(
        UUID id,
        UUID transferId,
        UUID correlationId,
        String ruleCode,
        Map<String, String> details,
        Instant detectedAt) {

    public static ReconciliationDiscrepancyResponse from(
            ReconciliationDiscrepancyRecord record) {

        return new ReconciliationDiscrepancyResponse(
                record.id(),
                record.transferId(),
                record.correlationId(),
                record.ruleCode(),
                record.details(),
                record.detectedAt());
    }
}