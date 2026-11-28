package com.fintech.reconciliation.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationAnomalyRecord(
        UUID runId,
        LocalDate businessDate,
        AnomalyScoreRecord score) {
}
