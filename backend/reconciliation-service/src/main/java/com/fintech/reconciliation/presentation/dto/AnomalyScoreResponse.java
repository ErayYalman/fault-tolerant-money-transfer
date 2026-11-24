package com.fintech.reconciliation.presentation.dto;

import java.time.Instant;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;

public record AnomalyScoreResponse(
        UUID transferId,
        UUID correlationId,
        double anomalyScore,
        String modelVersion,
        boolean isAnomaly,
        Instant scoredAt
) {
    public static AnomalyScoreResponse from(AnomalyScoreRecord record) {
        return new AnomalyScoreResponse(
                record.transferId(),
                record.correlationId(),
                record.anomalyScore(),
                record.modelVersion(),
                record.isAnomaly(),
                record.scoredAt()
        );
    }
}
