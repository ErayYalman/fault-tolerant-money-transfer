package com.fintech.reconciliation.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;
import com.fintech.reconciliation.domain.port.out.ReconciliationRunStore;
import com.fintech.reconciliation.presentation.dto.AnomalySummaryResponse;
import com.fintech.reconciliation.presentation.dto.ReconciliationDiscrepancyResponse;
import com.fintech.reconciliation.presentation.dto.ReconciliationRunDetailsResponse;

@Service
public class ReconciliationRunQueryService {

        private final ReconciliationRunStore runStore;

        public ReconciliationRunQueryService(ReconciliationRunStore runStore) {
                this.runStore = runStore;
        }

        public ReconciliationRunDetailsResponse getById(UUID runId) {
                ReconciliationRunRecord run = runStore.findById(runId)
                                .orElseThrow(() -> new ReconciliationRunNotFoundException(runId));

                List<ReconciliationDiscrepancyResponse> discrepancies = runStore.findDiscrepancies(runId)
                                .stream()
                                .map(ReconciliationDiscrepancyResponse::from)
                                .toList();

                List<AnomalyScoreRecord> scores = runStore.findAnomalyScores(runId);

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
                                anomalySummary);
        }
}