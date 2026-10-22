package com.fintech.reconciliation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AnomalyScoreRecord(
        UUID correlationId,
        double anomalyScore,
        String modelVersion,
        boolean isAnomaly,
        Instant scoredAt) {
}