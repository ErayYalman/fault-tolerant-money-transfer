package com.fintech.reconciliation.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRunEntity {

    @Id
    private UUID id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "run_type", nullable = false, length = 20)
    private String runType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "transfers_checked", nullable = false)
    private int transfersChecked;

    @Column(name = "discrepancies_found", nullable = false)
    private int discrepanciesFound;

    @Column(name = "anomalies_found", nullable = false)
    private int anomaliesFound;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReconciliationRunEntity() {
        // Required by JPA.
    }

    public static ReconciliationRunEntity start(
            LocalDate businessDate,
            Instant periodStart,
            Instant periodEnd,
            String runType,
            Instant now) {

        ReconciliationRunEntity run = new ReconciliationRunEntity();
        run.id = UUID.randomUUID();
        run.businessDate = businessDate;
        run.periodStart = periodStart;
        run.periodEnd = periodEnd;
        run.runType = runType;
        run.status = "RUNNING";
        run.transfersChecked = 0;
        run.discrepanciesFound = 0;
        run.anomaliesFound = 0;
        run.startedAt = now;
        run.createdAt = now;

        return run;
    }

    public void complete(
            int transfersChecked,
            int discrepanciesFound,
            int anomaliesFound,
            Instant finishedAt) {

        this.status = "COMPLETED";
        this.transfersChecked = transfersChecked;
        this.discrepanciesFound = discrepanciesFound;
        this.anomaliesFound = anomaliesFound;
        this.finishedAt = finishedAt;
        this.errorMessage = null;
    }

    public void fail(String errorMessage, Instant finishedAt) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.finishedAt = finishedAt;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public String getRunType() {
        return runType;
    }

    public String getStatus() {
        return status;
    }

    public int getTransfersChecked() {
        return transfersChecked;
    }

    public int getDiscrepanciesFound() {
        return discrepanciesFound;
    }

    public int getAnomaliesFound() {
        return anomaliesFound;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}