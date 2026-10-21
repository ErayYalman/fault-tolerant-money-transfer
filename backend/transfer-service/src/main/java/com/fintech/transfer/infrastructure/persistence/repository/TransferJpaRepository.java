package com.fintech.transfer.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fintech.transfer.infrastructure.persistence.entity.TransferJpaEntity;

public interface TransferJpaRepository
        extends JpaRepository<TransferJpaEntity, UUID> {

    Optional<TransferJpaEntity> findByIdempotencyKey(
            String idempotencyKey);

    Optional<TransferJpaEntity> findByCorrelationId(
            UUID correlationId);

    @Query("""
            SELECT t
            FROM TransferJpaEntity t
            WHERE
                (t.createdAt >= :fromInclusive
                    AND t.createdAt < :toExclusive)
                OR (t.updatedAt >= :fromInclusive
                    AND t.updatedAt < :toExclusive)
                OR (t.startedAt >= :fromInclusive
                    AND t.startedAt < :toExclusive)
                OR (t.debitRequestedAt >= :fromInclusive
                    AND t.debitRequestedAt < :toExclusive)
                OR (t.debitCompletedAt >= :fromInclusive
                    AND t.debitCompletedAt < :toExclusive)
                OR (t.creditRequestedAt >= :fromInclusive
                    AND t.creditRequestedAt < :toExclusive)
                OR (t.creditCompletedAt >= :fromInclusive
                    AND t.creditCompletedAt < :toExclusive)
                OR (t.completedAt >= :fromInclusive
                    AND t.completedAt < :toExclusive)
            ORDER BY t.createdAt ASC, t.id ASC
            """)
    List<TransferJpaEntity> findByLifecycleRange(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable);
}