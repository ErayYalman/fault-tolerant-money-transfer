package com.fintech.reconciliation.domain.model;

import java.util.List;
import java.util.Objects;

public record ReconciliationAnalysisResult(
        int transfersChecked,
        List<ReconciliationDiscrepancy> discrepancies) {

    public ReconciliationAnalysisResult {
        if (transfersChecked < 0) {
            throw new IllegalArgumentException(
                    "transfersChecked must not be negative");
        }

        discrepancies = List.copyOf(
                Objects.requireNonNull(
                        discrepancies,
                        "discrepancies must not be null"));
    }

    public int discrepanciesFound() {
        return discrepancies.size();
    }
}