package com.fintech.reconciliation.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;
import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancyRecord;
import com.fintech.reconciliation.domain.model.ReconciliationPeriod;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;

public interface ReconciliationRunStore {

    ReconciliationRunRecord startRun(
            ReconciliationPeriod period,
            String runType,
            Instant now);

    void completeRun(
            UUID runId,
            ReconciliationAnalysisResult result,
            Instant now);

    void failRun(
            UUID runId,
            String errorMessage,
            Instant now);

    Optional<ReconciliationRunRecord> findById(UUID runId);

    List<ReconciliationDiscrepancyRecord> findDiscrepancies(UUID runId);

    List<AnomalyScoreRecord> findAnomalyScores(UUID runId);
}