package com.fintech.reconciliation.presentation.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.AnomalyScoringStatus;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;

public record ReconciliationDailySummaryItemResponse(
        UUID runId,
        LocalDate businessDate,
        int transfersChecked,
        int discrepanciesFound,
        int anomaliesFound,
        AnomalyScoringStatus anomalyScoringStatus,
        int anomalyScoringAttemptedCount,
        int anomalyScoringFailedCount) {

    public static ReconciliationDailySummaryItemResponse from(
            ReconciliationRunRecord run) {

        return new ReconciliationDailySummaryItemResponse(
                run.runId(),
                run.businessDate(),
                run.transfersChecked(),
                run.discrepanciesFound(),
                run.anomaliesFound(),
                run.anomalyScoringStatus(),
                run.anomalyScoringAttemptedCount(),
                run.anomalyScoringFailedCount());
    }
}
