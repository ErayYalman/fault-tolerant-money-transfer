package com.fintech.reconciliation.infrastructure.persistence.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface ReconciliationAnomalyProjection {

    UUID getRunId();

    LocalDate getBusinessDate();

    UUID getTransferId();

    UUID getCorrelationId();

    double getAnomalyScore();

    String getModelVersion();

    boolean getAnomaly();

    Instant getScoredAt();
}
