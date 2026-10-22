package com.fintech.reconciliation.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationPeriod;
import com.fintech.reconciliation.domain.model.ReconciliationRunRecord;
import com.fintech.reconciliation.domain.port.out.ReconciliationRunStore;
import com.fintech.reconciliation.presentation.dto.ReconciliationRunDetailsResponse;

@Service
public class ReconciliationRunService {

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

        ReconciliationPeriod period = periodFactory.forBusinessDate(businessDate);

        ReconciliationRunRecord run = runStore.startRun(
                period,
                "MANUAL",
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