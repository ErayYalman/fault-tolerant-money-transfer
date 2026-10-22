package com.fintech.reconciliation.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;

public record ReconciliationRunDetailsResponse(
        UUID runId,
        LocalDate businessDate,
        Instant periodStart,
        Instant periodEnd,
        String runType,
        String status,
        int transfersChecked,
        int discrepanciesFound,
        int anomaliesFound,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        List<ReconciliationDiscrepancyResponse> discrepancies,
        AnomalySummaryResponse anomalySummary) {

    public ReconciliationRunDetailsResponse {
        discrepancies = List.copyOf(discrepancies);
    }

    public static ReconciliationRunDetailsResponse from(
            ReconciliationRunRecord run,
            List<ReconciliationDiscrepancyResponse> discrepancies,
            AnomalySummaryResponse anomalySummary) {

        return new ReconciliationRunDetailsResponse(
                run.runId(),
                run.businessDate(),
                run.periodStart(),
                run.periodEnd(),
                run.runType(),
                run.status(),
                run.transfersChecked(),
                run.discrepanciesFound(),
                run.anomaliesFound(),
                run.startedAt(),
                run.finishedAt(),
                run.errorMessage(),
                discrepancies,
                anomalySummary);
    }
}