package com.fintech.reconciliation.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_discrepancies")
public class ReconciliationDiscrepancyEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "rule_code", nullable = false, length = 40)
    private String ruleCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> details;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected ReconciliationDiscrepancyEntity() {
        // Required by JPA.
    }

    public static ReconciliationDiscrepancyEntity from(
            UUID runId,
            ReconciliationDiscrepancy discrepancy,
            Instant detectedAt) {

        ReconciliationDiscrepancyEntity entity = new ReconciliationDiscrepancyEntity();

        entity.id = UUID.randomUUID();
        entity.runId = runId;
        entity.transferId = discrepancy.transferId();
        entity.correlationId = discrepancy.correlationId();
        entity.ruleCode = discrepancy.ruleCode().name();
        entity.details = discrepancy.details();
        entity.detectedAt = detectedAt;

        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}