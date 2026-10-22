package com.fintech.reconciliation.domain.model;

import java.util.List;
import java.util.Objects;

public record AnomalyScoringResult(
        List<AnomalyScoreRecord> scores,
        int attemptedCount,
        int failedCount,
        String errorMessage) {

    public AnomalyScoringResult {
        scores = List.copyOf(
                Objects.requireNonNull(scores, "scores must not be null"));

        if (attemptedCount < 0 || failedCount < 0) {
            throw new IllegalArgumentException(
                    "Scoring counts must not be negative");
        }

        if (failedCount > attemptedCount
                || attemptedCount != scores.size() + failedCount) {
            throw new IllegalArgumentException(
                    "Scoring counts are inconsistent");
        }

        if (failedCount == 0) {
            errorMessage = null;
        } else if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = "One or more ML scoring attempts failed";
        }
    }

    public AnomalyScoringStatus status() {
        if (attemptedCount == 0) {
            return AnomalyScoringStatus.NOT_REQUIRED;
        }

        if (failedCount == 0) {
            return AnomalyScoringStatus.COMPLETED;
        }

        if (scores.isEmpty()) {
            return AnomalyScoringStatus.UNAVAILABLE;
        }

        return AnomalyScoringStatus.PARTIAL;
    }

    public static AnomalyScoringResult notRequired() {
        return new AnomalyScoringResult(
                List.of(),
                0,
                0,
                null);
    }

    public static AnomalyScoringResult fromSuccessfulScores(
            List<AnomalyScoreRecord> scores) {

        Objects.requireNonNull(scores, "scores must not be null");

        if (scores.isEmpty()) {
            return notRequired();
        }

        return new AnomalyScoringResult(
                scores,
                scores.size(),
                0,
                null);
    }
}