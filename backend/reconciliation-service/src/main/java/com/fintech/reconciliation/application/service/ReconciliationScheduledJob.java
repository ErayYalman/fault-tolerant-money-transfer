package com.fintech.reconciliation.application.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "reconciliation.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduledJob.class);

    private final ReconciliationRunService runService;
    private final ZoneId zoneId;

    public ReconciliationScheduledJob(
            ReconciliationRunService runService,
            @Value("${reconciliation.time-zone:Europe/Istanbul}") String timeZone) {

        this.runService = runService;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Scheduled(cron = "${reconciliation.schedule.cron:0 0 3 * * *}", zone = "${reconciliation.time-zone:Europe/Istanbul}")
    public void runPreviousBusinessDay() {
        LocalDate businessDate = LocalDate.now(zoneId).minusDays(1);

        try {
            var result = runService.runScheduled(businessDate);

            log.info(
                    "Scheduled reconciliation completed. runId={}, businessDate={}, "
                            + "transfersChecked={}, discrepanciesFound={}, anomaliesFound={}",
                    result.runId(),
                    businessDate,
                    result.transfersChecked(),
                    result.discrepanciesFound(),
                    result.anomaliesFound());

        } catch (RuntimeException exception) {
            log.error(
                    "Scheduled reconciliation failed for businessDate={}",
                    businessDate,
                    exception);
        }
    }
}