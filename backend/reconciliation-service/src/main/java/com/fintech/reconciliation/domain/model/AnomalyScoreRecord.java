package com.fintech.reconciliation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AnomalyScoreRecord(
                UUID transferId,
                UUID correlationId,
                double anomalyScore,
                String modelVersion,
                boolean isAnomaly,
                Instant scoredAt) {

        public AnomalyScoreRecord {
                Objects.requireNonNull(transferId, "transferId must not be null");
                Objects.requireNonNull(correlationId, "correlationId must not be null");
                Objects.requireNonNull(modelVersion, "modelVersion must not be null");
                Objects.requireNonNull(scoredAt, "scoredAt must not be null");

                if (!Double.isFinite(anomalyScore)
                                || anomalyScore < 0
                                || anomalyScore > 1) {
                        throw new IllegalArgumentException(
                                        "anomalyScore must be between 0 and 1");
                }
        }
}