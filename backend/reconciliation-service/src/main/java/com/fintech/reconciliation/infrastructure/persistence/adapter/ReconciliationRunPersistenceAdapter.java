package com.fintech.reconciliation.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;
import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancyRecord;
import com.fintech.reconciliation.domain.model.ReconciliationPeriod;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;
import com.fintech.reconciliation.domain.port.out.ReconciliationRunStore;
import com.fintech.reconciliation.infrastructure.persistence.entity.AnomalyScoreEntity;
import com.fintech.reconciliation.infrastructure.persistence.entity.ReconciliationDiscrepancyEntity;
import com.fintech.reconciliation.infrastructure.persistence.entity.ReconciliationRunEntity;
import com.fintech.reconciliation.infrastructure.persistence.repository.AnomalyScoreRepository;
import com.fintech.reconciliation.infrastructure.persistence.repository.ReconciliationDiscrepancyRepository;
import com.fintech.reconciliation.infrastructure.persistence.repository.ReconciliationRunRepository;

@Component
public class ReconciliationRunPersistenceAdapter
                implements ReconciliationRunStore {

        private final ReconciliationRunRepository runRepository;
        private final ReconciliationDiscrepancyRepository discrepancyRepository;
        private final AnomalyScoreRepository anomalyScoreRepository;

        public ReconciliationRunPersistenceAdapter(
                        ReconciliationRunRepository runRepository,
                        ReconciliationDiscrepancyRepository discrepancyRepository,
                        AnomalyScoreRepository anomalyScoreRepository) {
                this.runRepository = runRepository;
                this.discrepancyRepository = discrepancyRepository;
                this.anomalyScoreRepository = anomalyScoreRepository;
        }

        @Override
        @Transactional
        public ReconciliationRunRecord startRun(
                        ReconciliationPeriod period,
                        String runType,
                        Instant now) {

                ReconciliationRunEntity run = ReconciliationRunEntity.start(
                                period.businessDate(),
                                period.fromInclusive(),
                                period.toExclusive(),
                                runType,
                                now);

                return toRecord(runRepository.save(run));
        }

        @Override
        @Transactional
        public void completeRun(
                        UUID runId,
                        ReconciliationAnalysisResult result,
                        Instant now) {

                ReconciliationRunEntity run = runRepository.findById(runId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Reconciliation run not found: " + runId));

                List<ReconciliationDiscrepancyEntity> discrepancies = result.discrepancies().stream()
                                .map(discrepancy -> ReconciliationDiscrepancyEntity.from(
                                                runId,
                                                discrepancy,
                                                now))
                                .toList();

                List<AnomalyScoreEntity> anomalyScores = result.anomalyScores().stream()
                                .map(score -> AnomalyScoreEntity.from(runId, score))
                                .toList();

                discrepancyRepository.saveAll(discrepancies);
                anomalyScoreRepository.saveAll(anomalyScores);

                run.complete(
                                result.transfersChecked(),
                                result.discrepanciesFound(),
                                result.anomaliesFound(),
                                result.anomalyScoringResult(),
                                now);

                runRepository.save(run);
        }

        @Override
        @Transactional
        public void failRun(
                        UUID runId,
                        String errorMessage,
                        Instant now) {

                ReconciliationRunEntity run = runRepository.findById(runId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Reconciliation run not found: " + runId));

                String message = errorMessage == null || errorMessage.isBlank()
                                ? "Reconciliation execution failed"
                                : errorMessage;

                if (message.length() > 4000) {
                        message = message.substring(0, 4000);
                }

                run.fail(message, now);
                runRepository.save(run);
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<ReconciliationRunRecord> findById(UUID runId) {
                return runRepository.findById(runId).map(
                                ReconciliationRunPersistenceAdapter::toRecord);
        }

        @Override
        @Transactional(readOnly = true)
        public List<ReconciliationDiscrepancyRecord> findDiscrepancies(
                        UUID runId) {

                return discrepancyRepository
                                .findByRunIdOrderByDetectedAtAsc(runId)
                                .stream()
                                .map(entity -> new ReconciliationDiscrepancyRecord(
                                                entity.getId(),
                                                entity.getTransferId(),
                                                entity.getCorrelationId(),
                                                entity.getRuleCode(),
                                                entity.getDetails(),
                                                entity.getDetectedAt()))
                                .toList();
        }

        @Override
        @Transactional(readOnly = true)
        public List<AnomalyScoreRecord> findAnomalyScores(UUID runId) {

                return anomalyScoreRepository
                                .findByRunIdOrderByScoredAtAsc(runId)
                                .stream()
                                .map(ReconciliationRunPersistenceAdapter::toScoreRecord)
                                .toList();
        }

        private static ReconciliationRunRecord toRecord(
                        ReconciliationRunEntity entity) {

                return new ReconciliationRunRecord(
                                entity.getId(),
                                entity.getBusinessDate(),
                                entity.getPeriodStart(),
                                entity.getPeriodEnd(),
                                entity.getRunType(),
                                entity.getStatus(),
                                entity.getTransfersChecked(),
                                entity.getDiscrepanciesFound(),
                                entity.getAnomaliesFound(),
                                entity.getAnomalyScoringStatus(),
                                entity.getAnomalyScoringAttemptedCount(),
                                entity.getAnomalyScoringFailedCount(),
                                entity.getAnomalyScoringErrorMessage(),
                                entity.getStartedAt(),
                                entity.getFinishedAt(),
                                entity.getErrorMessage());
        }

        private static AnomalyScoreRecord toScoreRecord(
                        AnomalyScoreEntity entity) {

                return new AnomalyScoreRecord(
                                entity.getTransferId(),
                                entity.getCorrelationId(),
                                entity.getAnomalyScore(),
                                entity.getModelVersion(),
                                entity.isAnomaly(),
                                entity.getScoredAt());
        }
}