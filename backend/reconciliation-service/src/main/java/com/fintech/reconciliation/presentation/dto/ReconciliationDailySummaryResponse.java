package com.fintech.reconciliation.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import com.fintech.reconciliation.domain.model.AnomalyScoringStatus;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;

public record ReconciliationDailySummaryResponse(
        LocalDate fromDate,
        LocalDate toDate,
        int completedDays,
        long transfersChecked,
        long discrepanciesFound,
        long anomaliesFound,
        long anomalyScoringAttemptedCount,
        long anomalyScoringFailedCount,
        int incompleteAnomalyScoringDays,
        List<ReconciliationDailySummaryItemResponse> daily) {

    public ReconciliationDailySummaryResponse {
        daily = List.copyOf(daily);
    }

    public static ReconciliationDailySummaryResponse from(
            LocalDate fromDate,
            LocalDate toDate,
            List<ReconciliationRunRecord> runs) {

        List<ReconciliationDailySummaryItemResponse> daily = runs.stream()
                .map(ReconciliationDailySummaryItemResponse::from)
                .toList();

        long transfersChecked = runs.stream()
                .mapToLong(ReconciliationRunRecord::transfersChecked)
                .sum();

        long discrepanciesFound = runs.stream()
                .mapToLong(ReconciliationRunRecord::discrepanciesFound)
                .sum();

        long anomaliesFound = runs.stream()
                .mapToLong(ReconciliationRunRecord::anomaliesFound)
                .sum();

        long scoringAttempted = runs.stream()
                .mapToLong(ReconciliationRunRecord::anomalyScoringAttemptedCount)
                .sum();

        long scoringFailed = runs.stream()
                .mapToLong(ReconciliationRunRecord::anomalyScoringFailedCount)
                .sum();

        int incompleteScoringDays = (int) runs.stream()
                .filter(run ->
                        run.anomalyScoringStatus() == AnomalyScoringStatus.PARTIAL
                        || run.anomalyScoringStatus() == AnomalyScoringStatus.UNAVAILABLE)
                .count();

        return new ReconciliationDailySummaryResponse(
                fromDate,
                toDate,
                runs.size(),
                transfersChecked,
                discrepanciesFound,
                anomaliesFound,
                scoringAttempted,
                scoringFailed,
                incompleteScoringDays,
                daily);
    }
}
