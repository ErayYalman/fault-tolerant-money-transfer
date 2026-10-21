package com.fintech.payment.b.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fintech.payment.b.domain.model.LedgerEntry;
import com.fintech.payment.b.domain.port.out.LedgerEntryRepository;
import com.fintech.payment.b.infrastructure.persistence.mapper.LedgerEntryPersistenceMapper;
import com.fintech.payment.b.infrastructure.persistence.repository.LedgerEntryPanacheRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LedgerEntryPersistenceAdapter implements LedgerEntryRepository {

    private final LedgerEntryPanacheRepository repository;
    private final LedgerEntryPersistenceMapper mapper;

    public LedgerEntryPersistenceAdapter(LedgerEntryPanacheRepository repository, LedgerEntryPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void save(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");

        repository.persist(mapper.toEntity(entry));
    }

    @Override
    public List<LedgerEntry> findByCreatedAtRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size) {

        return repository.findByCreatedAtRange(
                fromInclusive,
                toExclusive,
                page,
                size)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}