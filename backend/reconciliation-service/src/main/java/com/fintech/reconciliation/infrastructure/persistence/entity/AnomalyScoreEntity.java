package com.fintech.reconciliation.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "anomaly_scores")
public class AnomalyScoreEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "anomaly_score", nullable = false)
    private double anomalyScore;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "is_anomaly", nullable = false)
    private boolean anomaly;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    protected AnomalyScoreEntity() {
        // Required by JPA.
    }

    public static AnomalyScoreEntity from(UUID runId, AnomalyScoreRecord record) {

        AnomalyScoreEntity entity = new AnomalyScoreEntity();

        entity.id = UUID.randomUUID();
        entity.runId = runId;
        entity.transferId = record.transferId();
        entity.correlationId = record.correlationId();
        entity.anomalyScore = record.anomalyScore();
        entity.modelVersion = record.modelVersion();
        entity.anomaly = record.isAnomaly();
        entity.scoredAt = record.scoredAt();

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

    public double getAnomalyScore() {
        return anomalyScore;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public boolean isAnomaly() {
        return anomaly;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }
}