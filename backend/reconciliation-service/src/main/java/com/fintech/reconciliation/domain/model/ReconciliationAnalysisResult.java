package com.fintech.reconciliation.domain.model;

import java.util.List;
import java.util.Objects;

public record ReconciliationAnalysisResult(
        int transfersChecked,
        List<ReconciliationDiscrepancy> discrepancies,
        List<AnomalyScoreRecord> anomalyScores) {

    public ReconciliationAnalysisResult {
        if (transfersChecked < 0) {
            throw new IllegalArgumentException(
                    "transfersChecked must not be negative");
        }

        discrepancies = List.copyOf(
                Objects.requireNonNull(
                        discrepancies,
                        "discrepancies must not be null"));

        anomalyScores = List.copyOf(
                Objects.requireNonNull(
                        anomalyScores,
                        "anomalyScores must not be null"));
    }

    // Keeps existing tests/call sites that do not supply ML results compatible.
    public ReconciliationAnalysisResult(
            int transfersChecked,
            List<ReconciliationDiscrepancy> discrepancies) {
        this(transfersChecked, discrepancies, List.of());
    }

    public int discrepanciesFound() {
        return discrepancies.size();
    }

    public int anomaliesFound() {
        return Math.toIntExact(anomalyScores.stream()
                .filter(AnomalyScoreRecord::isAnomaly)
                .count());
    }
}