package com.fintech.reconciliation.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fintech.reconciliation.infrastructure.persistence.entity.ReconciliationRunEntity;

public interface ReconciliationRunRepository
        extends JpaRepository<ReconciliationRunEntity, UUID> {

    @Query(
        value = """
            SELECT run
            FROM ReconciliationRunEntity run
            WHERE run.businessDate >= :fromDate
              AND run.businessDate <= :toDate
            ORDER BY
              run.businessDate DESC,
              run.startedAt DESC,
              run.id DESC
            """,
        countQuery = """
            SELECT COUNT(run)
            FROM ReconciliationRunEntity run
            WHERE run.businessDate >= :fromDate
              AND run.businessDate <= :toDate
            """
    )
    Page<ReconciliationRunEntity> findRunsInBusinessDateRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query(value = """
            SELECT DISTINCT ON (r.business_date) r.*
            FROM reconciliation_runs r
            WHERE r.business_date BETWEEN :fromDate AND :toDate
              AND r.status = 'COMPLETED'
            ORDER BY
              r.business_date ASC,
              r.started_at DESC,
              r.id DESC
            """, nativeQuery = true)
    List<ReconciliationRunEntity> findLatestCompletedRuns(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
