package com.fintech.reconciliation.presentation.dto;

import java.util.List;

public record AnomalySummaryResponse(
        String status,
        int attemptedCount,
        int failedCount,
        String errorMessage,
        long scoredCount,
        long anomalyCount,
        List<String> modelVersions) {

    public AnomalySummaryResponse {
        modelVersions = List.copyOf(modelVersions);
    }
}