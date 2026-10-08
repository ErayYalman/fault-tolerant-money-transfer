package com.fintech.transfer.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.domain.port.out.SagaStateRepository;
import com.fintech.transfer.infrastructure.persistence.mapper.SagaStatePersistenceMapper;
import com.fintech.transfer.infrastructure.persistence.repository.SagaStateJpaRepository;

@Repository
public class SagaStatePersistenceAdapter
        implements SagaStateRepository {

    private final SagaStateJpaRepository repository;
    private final SagaStatePersistenceMapper mapper;

    public SagaStatePersistenceAdapter(
            SagaStateJpaRepository repository,
            SagaStatePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SagaState save(SagaState sagaState) {
        return mapper.toDomain(
                repository.save(
                        mapper.toEntity(sagaState)));
    }

    @Override
    public Optional<SagaState> findByTransferId(
            UUID transferId) {
        return repository.findByTransferId(transferId)
                .map(mapper::toDomain);
    }
}