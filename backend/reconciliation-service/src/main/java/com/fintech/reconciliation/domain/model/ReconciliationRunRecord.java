package com.fintech.reconciliation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationRunRecord(
                UUID runId,
                LocalDate businessDate,
                Instant periodStart,
                Instant periodEnd,
                String runType,
                String status,
                int transfersChecked,
                int discrepanciesFound,
                int anomaliesFound,
                AnomalyScoringStatus anomalyScoringStatus,
                int anomalyScoringAttemptedCount,
                int anomalyScoringFailedCount,
                String anomalyScoringErrorMessage,
                Instant startedAt,
                Instant finishedAt,
                String errorMessage) {
}