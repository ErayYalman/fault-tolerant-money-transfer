package com.fintech.reconciliation.presentation.dto;

import java.util.List;

public record AnomalySummaryResponse(
        long scoredCount,
        long anomalyCount,
        List<String> modelVersions) {

    public AnomalySummaryResponse {
        modelVersions = List.copyOf(modelVersions);
    }
}