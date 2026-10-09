package com.fintech.transfer.infrastructure.persistence.adapter;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.out.TransferRepository;
import com.fintech.transfer.infrastructure.persistence.mapper.TransferPersistenceMapper;
import com.fintech.transfer.infrastructure.persistence.repository.TransferJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TransferPersistenceAdapter implements TransferRepository {

        private final TransferJpaRepository repository;
        private final TransferPersistenceMapper mapper;

        public TransferPersistenceAdapter(
                        TransferJpaRepository repository,
                        TransferPersistenceMapper mapper) {
                this.repository = repository;
                this.mapper = mapper;
        }

        @Override
        public Transfer save(Transfer transfer) {
                return mapper.toDomain(
                                repository.save(
                                                mapper.toEntity(transfer)));
        }

        @Override
        public Optional<Transfer> findById(UUID transferId) {
                return repository.findById(transferId)
                                .map(mapper::toDomain);
        }

        @Override
        public Optional<Transfer> findByIdempotencyKey(
                        String idempotencyKey) {
                return repository.findByIdempotencyKey(idempotencyKey)
                                .map(mapper::toDomain);
        }

        @Override
        public Optional<Transfer> findByCorrelationId(UUID correlationId) {
                return repository.findByCorrelationId(correlationId)
                                .map(mapper::toDomain);
        }

}