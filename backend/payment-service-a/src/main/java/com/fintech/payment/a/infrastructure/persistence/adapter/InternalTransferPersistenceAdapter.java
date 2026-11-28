package com.fintech.payment.a.infrastructure.persistence.adapter;

import com.fintech.payment.a.domain.model.InternalTransfer;
import com.fintech.payment.a.domain.port.out.InternalTransferRepository;
import com.fintech.payment.a.infrastructure.persistence.mapper.InternalTransferPersistenceMapper;
import com.fintech.payment.a.infrastructure.persistence.repository.InternalTransferJpaRepository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class InternalTransferPersistenceAdapter
        implements InternalTransferRepository {

    private final InternalTransferJpaRepository repository;
    private final InternalTransferPersistenceMapper mapper;

    public InternalTransferPersistenceAdapter(
            InternalTransferJpaRepository repository,
            InternalTransferPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InternalTransfer save(InternalTransfer transfer) {
        return mapper.toDomain(
                repository.save(mapper.toEntity(transfer)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InternalTransfer> findByOwnerSubjectAndIdempotencyKey(
            String ownerSubject,
            String idempotencyKey) {

        return repository.findByOwnerSubjectAndIdempotencyKey(
                ownerSubject,
                idempotencyKey)
                .map(mapper::toDomain);
    }
}
