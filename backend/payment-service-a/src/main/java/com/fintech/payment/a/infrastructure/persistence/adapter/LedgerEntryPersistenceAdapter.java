package com.fintech.payment.a.infrastructure.persistence.adapter;

import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.domain.port.out.LedgerEntryRepository;
import com.fintech.payment.a.infrastructure.persistence.mapper.LedgerEntryPersistenceMapper;
import com.fintech.payment.a.infrastructure.persistence.repository.LedgerEntryJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LedgerEntryPersistenceAdapter implements LedgerEntryRepository {

    private final LedgerEntryJpaRepository repository;
    private final LedgerEntryPersistenceMapper mapper;

    public LedgerEntryPersistenceAdapter(
            LedgerEntryJpaRepository repository,
            LedgerEntryPersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(LedgerEntry entry) {
        repository.save(mapper.toEntity(entry));
    }
}