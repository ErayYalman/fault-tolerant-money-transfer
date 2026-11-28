package com.fintech.reconciliation.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fintech.reconciliation.infrastructure.persistence.entity.AnomalyScoreEntity;

public interface AnomalyScoreRepository
        extends JpaRepository<AnomalyScoreEntity, UUID> {

    List<AnomalyScoreEntity> findByRunIdOrderByScoredAtAscTransferIdAsc(
            UUID runId);

    @Query(
        value = """
                WITH latest_completed_runs AS (
                    SELECT DISTINCT ON (r.business_date)
                        r.id,
                        r.business_date
                    FROM reconciliation_runs r
                    WHERE r.business_date BETWEEN :fromDate AND :toDate
                      AND r.status = 'COMPLETED'
                    ORDER BY
                        r.business_date ASC,
                        r.started_at DESC,
                        r.id DESC
                )
                SELECT
                    a.run_id AS "runId",
                    latest.business_date AS "businessDate",
                    a.transfer_id AS "transferId",
                    a.correlation_id AS "correlationId",
                    a.anomaly_score AS "anomalyScore",
                    a.model_version AS "modelVersion",
                    a.is_anomaly AS "anomaly",
                    a.scored_at AS "scoredAt"
                FROM anomaly_scores a
                INNER JOIN latest_completed_runs latest
                    ON latest.id = a.run_id
                WHERE
                    (
                        CAST(:isAnomaly AS BOOLEAN) IS NULL
                        OR a.is_anomaly = CAST(:isAnomaly AS BOOLEAN)
                    )
                ORDER BY
                    a.scored_at DESC,
                    a.transfer_id ASC,
                    a.id ASC
                """,
        countQuery = """
                WITH latest_completed_runs AS (
                    SELECT DISTINCT ON (r.business_date)
                        r.id,
                        r.business_date
                    FROM reconciliation_runs r
                    WHERE r.business_date BETWEEN :fromDate AND :toDate
                      AND r.status = 'COMPLETED'
                    ORDER BY
                        r.business_date ASC,
                        r.started_at DESC,
                        r.id DESC
                )
                SELECT COUNT(*)
                FROM anomaly_scores a
                INNER JOIN latest_completed_runs latest
                    ON latest.id = a.run_id
                WHERE
                    (
                        CAST(:isAnomaly AS BOOLEAN) IS NULL
                        OR a.is_anomaly = CAST(:isAnomaly AS BOOLEAN)
                    )
                """,
        nativeQuery = true
)
Page<ReconciliationAnomalyProjection> findLatestCompletedRunScores(
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @Param("isAnomaly") Boolean isAnomaly,
        Pageable pageable);
}
