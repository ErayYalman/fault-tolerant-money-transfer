package com.fintech.reconciliation.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationPeriod;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;
import com.fintech.reconciliation.domain.port.out.ReconciliationRunStore;
import com.fintech.reconciliation.presentation.dto.ReconciliationRunDetailsResponse;

@Service
public class ReconciliationRunService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationRunService.class);

    private final ReconciliationPeriodFactory periodFactory;
    private final ReconciliationAnalysisService analysisService;
    private final ReconciliationRunStore runStore;
    private final ReconciliationRunQueryService queryService;

    public ReconciliationRunService(
            ReconciliationPeriodFactory periodFactory,
            ReconciliationAnalysisService analysisService,
            ReconciliationRunStore runStore,
            ReconciliationRunQueryService queryService) {

        this.periodFactory = periodFactory;
        this.analysisService = analysisService;
        this.runStore = runStore;
        this.queryService = queryService;
    }

    public ReconciliationRunDetailsResponse runManual(
            LocalDate businessDate) {
        return run(businessDate, "MANUAL");
    }

    public ReconciliationRunDetailsResponse runScheduled(
            LocalDate businessDate) {
        return run(businessDate, "SCHEDULED");
    }

    private ReconciliationRunDetailsResponse run(
            LocalDate businessDate,
            String runType) {

        ReconciliationPeriod period = periodFactory.forBusinessDate(businessDate);

        ReconciliationRunRecord run = runStore.startRun(
                period,
                runType,
                Instant.now());

        try {
            ReconciliationAnalysisResult result = analysisService.analyze(
                    period.fromInclusive(),
                    period.toExclusive());

            runStore.completeRun(
                    run.runId(),
                    result,
                    Instant.now());

        } catch (RuntimeException exception) {
            log.error("Mutabakat çalıştırılamadı [businessDate: {}]: {}", businessDate, exception.getMessage(), exception);

            markFailedSafely(run.runId(), exception);

            throw new ReconciliationExecutionException(
                    run.runId(),
                    exception);
        }

        return queryService.getById(run.runId());
    }

    private void markFailedSafely(
            UUID runId,
            RuntimeException originalException) {

        String errorMessage = originalException.getMessage();

        try {
            runStore.failRun(
                    runId,
                    errorMessage,
                    Instant.now());
        } catch (RuntimeException persistenceException) {
            originalException.addSuppressed(persistenceException);
        }
    }
}