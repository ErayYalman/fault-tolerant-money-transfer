package com.fintech.reconciliation.application.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;
import com.fintech.reconciliation.domain.model.ReconciliationAnomalyPage;
import com.fintech.reconciliation.domain.model.ReconciliationRunPage;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;
import com.fintech.reconciliation.domain.port.out.ReconciliationRunStore;
import com.fintech.reconciliation.presentation.dto.AnomalyScoreResponse;
import com.fintech.reconciliation.presentation.dto.AnomalySummaryResponse;
import com.fintech.reconciliation.presentation.dto.ReconciliationAnomaliesPageResponse;
import com.fintech.reconciliation.presentation.dto.ReconciliationDailySummaryResponse;
import com.fintech.reconciliation.presentation.dto.ReconciliationDiscrepancyResponse;
import com.fintech.reconciliation.presentation.dto.ReconciliationRunDetailsResponse;
import com.fintech.reconciliation.presentation.dto.ReconciliationRunsPageResponse;

@Service
public class ReconciliationRunQueryService {

    private final ReconciliationRunStore runStore;

    public ReconciliationRunQueryService(ReconciliationRunStore runStore) {
        this.runStore = runStore;
    }

    private static final long MAX_DATE_RANGE_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 100;

    public ReconciliationRunsPageResponse listRuns(
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {

        validateDateRange(fromDate, toDate);

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be greater than or equal to zero.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100.");
        }

        ReconciliationRunPage result = runStore.findRuns(
                fromDate,
                toDate,
                page,
                size);

        return ReconciliationRunsPageResponse.from(result);
    }

    public ReconciliationDailySummaryResponse getDailySummary(
            LocalDate fromDate,
            LocalDate toDate) {

        validateDateRange(fromDate, toDate);

        List<ReconciliationRunRecord> runs =
                runStore.findLatestCompletedRuns(fromDate, toDate);

        return ReconciliationDailySummaryResponse.from(
                fromDate,
                toDate,
                runs);
    }

    public ReconciliationAnomaliesPageResponse listAnomalies(
            LocalDate fromDate,
            LocalDate toDate,
            Boolean isAnomaly,
            int page,
            int size) {

        validateDateRange(fromDate, toDate);

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be greater than or equal to zero.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100.");
        }

        ReconciliationAnomalyPage result =
                runStore.findAnomalyScoresForLatestCompletedRuns(
                        fromDate,
                        toDate,
                        isAnomaly,
                        page,
                        size);

        return ReconciliationAnomaliesPageResponse.from(
                fromDate,
                toDate,
                isAnomaly,
                result);
    }

    private void validateDateRange(
            LocalDate fromDate,
            LocalDate toDate) {

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "fromDate and toDate are required.");
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must not be after toDate.");
        }

        long requestedDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        if (requestedDays > MAX_DATE_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Date range must not exceed 366 days.");
        }
    }

    public ReconciliationRunDetailsResponse getById(UUID runId) {
        ReconciliationRunRecord run = runStore.findById(runId)
                .orElseThrow(() -> new ReconciliationRunNotFoundException(runId));

        List<ReconciliationDiscrepancyResponse> discrepancies =
                runStore.findDiscrepancies(runId)
                        .stream()
                        .map(ReconciliationDiscrepancyResponse::from)
                        .toList();

        List<AnomalyScoreRecord> scores = runStore.findAnomalyScores(runId);

        List<AnomalyScoreResponse> anomalyScores = scores.stream()
                .map(AnomalyScoreResponse::from)
                .toList();

        AnomalySummaryResponse anomalySummary = new AnomalySummaryResponse(
                run.anomalyScoringStatus().name(),
                run.anomalyScoringAttemptedCount(),
                run.anomalyScoringFailedCount(),
                run.anomalyScoringErrorMessage(),
                scores.size(),
                scores.stream()
                        .filter(AnomalyScoreRecord::isAnomaly)
                        .count(),
                scores.stream()
                        .map(AnomalyScoreRecord::modelVersion)
                        .distinct()
                        .sorted()
                        .toList());

        return ReconciliationRunDetailsResponse.from(
                run,
                discrepancies,
                anomalyScores,
                anomalySummary);
    }
}
