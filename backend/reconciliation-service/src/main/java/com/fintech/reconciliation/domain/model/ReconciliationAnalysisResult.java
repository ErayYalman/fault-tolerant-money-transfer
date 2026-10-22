package com.fintech.reconciliation.domain.model;

import java.util.List;
import java.util.Objects;

public record ReconciliationAnalysisResult(
        int transfersChecked,
        List<ReconciliationDiscrepancy> discrepancies,
        AnomalyScoringResult anomalyScoringResult) {

    public ReconciliationAnalysisResult {
        if (transfersChecked < 0) {
            throw new IllegalArgumentException(
                    "transfersChecked must not be negative");
        }

        discrepancies = List.copyOf(
                Objects.requireNonNull(
                        discrepancies,
                        "discrepancies must not be null"));

        Objects.requireNonNull(
                anomalyScoringResult,
                "anomalyScoringResult must not be null");
    }

    public ReconciliationAnalysisResult(
            int transfersChecked,
            List<ReconciliationDiscrepancy> discrepancies) {

        this(
                transfersChecked,
                discrepancies,
                AnomalyScoringResult.notRequired());
    }

    // Existing callers that supply only scores remain compatible.
    public ReconciliationAnalysisResult(
            int transfersChecked,
            List<ReconciliationDiscrepancy> discrepancies,
            List<AnomalyScoreRecord> anomalyScores) {

        this(
                transfersChecked,
                discrepancies,
                AnomalyScoringResult.fromSuccessfulScores(anomalyScores));
    }

    public List<AnomalyScoreRecord> anomalyScores() {
        return anomalyScoringResult.scores();
    }

    public int discrepanciesFound() {
        return discrepancies.size();
    }

    public int anomaliesFound() {
        return Math.toIntExact(anomalyScoringResult.scores().stream()
                .filter(AnomalyScoreRecord::isAnomaly)
                .count());
    }
}