package com.fintech.reconciliation.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.ReconciliationAnomalyRecord;

public record ReconciliationAnomalyResponse(
        UUID runId,
        LocalDate businessDate,
        UUID transferId,
        UUID correlationId,
        double anomalyScore,
        String modelVersion,
        boolean isAnomaly,
        Instant scoredAt) {

    public static ReconciliationAnomalyResponse from(
            ReconciliationAnomalyRecord record) {

        return new ReconciliationAnomalyResponse(
                record.runId(),
                record.businessDate(),
                record.score().transferId(),
                record.score().correlationId(),
                record.score().anomalyScore(),
                record.score().modelVersion(),
                record.score().isAnomaly(),
                record.score().scoredAt());
    }
}
