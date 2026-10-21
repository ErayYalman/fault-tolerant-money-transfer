package com.fintech.payment.a.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.domain.model.LedgerEntryType;
import com.fintech.payment.a.domain.port.out.LedgerEntryRepository;
import com.fintech.payment.a.infrastructure.persistence.mapper.LedgerEntryPersistenceMapper;
import com.fintech.payment.a.infrastructure.persistence.repository.LedgerEntryJpaRepository;

@Repository
public class LedgerEntryPersistenceAdapter implements LedgerEntryRepository {

    private final LedgerEntryJpaRepository repository;
    private final LedgerEntryPersistenceMapper mapper;

    public LedgerEntryPersistenceAdapter(
            LedgerEntryJpaRepository repository,
            LedgerEntryPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(LedgerEntry entry) {
        repository.save(mapper.toEntity(entry));
    }

    @Override
    public Optional<LedgerEntry> findByCorrelationIdAndEntryType(
            UUID correlationId,
            LedgerEntryType entryType) {
        return repository.findByCorrelationIdAndEntryType(
                correlationId,
                entryType)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> findByCreatedAtRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size) {
        return repository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(page, size))
                .map(mapper::toDomain)
                .getContent();
    }
}