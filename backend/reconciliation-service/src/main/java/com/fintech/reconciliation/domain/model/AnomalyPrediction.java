package com.fintech.reconciliation.domain.model;

import java.util.Objects;

public record AnomalyPrediction(
        double anomalyScore,
        String modelVersion,
        boolean isAnomaly) {

    public AnomalyPrediction {
        if (!Double.isFinite(anomalyScore)
                || anomalyScore < 0
                || anomalyScore > 1) {
            throw new IllegalArgumentException(
                    "anomalyScore must be finite and between 0 and 1");
        }

        Objects.requireNonNull(modelVersion, "modelVersion must not be null");

        if (modelVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "modelVersion must not be blank");
        }
    }
}