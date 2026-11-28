package com.fintech.reconciliation.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.AnomalyScoringStatus;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;

public record ReconciliationRunSummaryResponse(
        UUID runId,
        LocalDate businessDate,
        Instant periodStart,
        Instant periodEnd,
        String runType,
        String status,
        int transfersChecked,
        int discrepanciesFound,
        int anomaliesFound,
        AnomalyScoringStatus anomalyScoringStatus,
        int anomalyScoringAttemptedCount,
        int anomalyScoringFailedCount,
        Instant startedAt,
        Instant finishedAt) {

    public static ReconciliationRunSummaryResponse from(
            ReconciliationRunRecord run) {

        return new ReconciliationRunSummaryResponse(
                run.runId(),
                run.businessDate(),
                run.periodStart(),
                run.periodEnd(),
                run.runType(),
                run.status(),
                run.transfersChecked(),
                run.discrepanciesFound(),
                run.anomaliesFound(),
                run.anomalyScoringStatus(),
                run.anomalyScoringAttemptedCount(),
                run.anomalyScoringFailedCount(),
                run.startedAt(),
                run.finishedAt());
    }
}
