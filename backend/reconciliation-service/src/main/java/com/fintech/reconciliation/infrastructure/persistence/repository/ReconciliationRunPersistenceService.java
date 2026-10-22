package com.fintech.reconciliation.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationPeriod;
import com.fintech.reconciliation.infrastructure.persistence.entity.ReconciliationDiscrepancyEntity;
import com.fintech.reconciliation.infrastructure.persistence.entity.ReconciliationRunEntity;

@Service
public class ReconciliationRunPersistenceService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;

    public ReconciliationRunPersistenceService(
            ReconciliationRunRepository runRepository,
            ReconciliationDiscrepancyRepository discrepancyRepository) {
        this.runRepository = runRepository;
        this.discrepancyRepository = discrepancyRepository;
    }

    @Transactional
    public ReconciliationRunEntity startRun(
            ReconciliationPeriod period,
            String runType) {

        ReconciliationRunEntity run = ReconciliationRunEntity.start(
                period.businessDate(),
                period.fromInclusive(),
                period.toExclusive(),
                runType,
                Instant.now());

        return runRepository.save(run);
    }

    @Transactional
    public void completeRun(
            UUID runId,
            ReconciliationAnalysisResult result) {

        ReconciliationRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException(
                        "Reconciliation run not found: " + runId));

        Instant finishedAt = Instant.now();

        List<ReconciliationDiscrepancyEntity> entities = result.discrepancies().stream()
                .map(discrepancy -> ReconciliationDiscrepancyEntity.from(
                        runId,
                        discrepancy,
                        finishedAt))
                .toList();

        discrepancyRepository.saveAll(entities);

        run.complete(
                result.transfersChecked(),
                result.discrepanciesFound(),
                result.anomaliesFound(),
                result.anomalyScoringResult(),
                finishedAt);

        runRepository.save(run);
    }

    @Transactional
    public void failRun(UUID runId, RuntimeException exception) {
        ReconciliationRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException(
                        "Reconciliation run not found: " + runId));

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        if (message.length() > 4000) {
            message = message.substring(0, 4000);
        }

        run.fail(message, Instant.now());
        runRepository.save(run);
    }
}