package com.fintech.payment.a.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.payment.a.domain.model.LedgerEntryType;
import com.fintech.payment.a.infrastructure.persistence.entity.LedgerEntryJpaEntity;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

        Optional<LedgerEntryJpaEntity> findByCorrelationIdAndEntryType(
                        UUID correlationId,
                        LedgerEntryType entryType);

        Page<LedgerEntryJpaEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        Instant fromInclusive,
                        Instant toExclusive,
                        Pageable pageable);
}