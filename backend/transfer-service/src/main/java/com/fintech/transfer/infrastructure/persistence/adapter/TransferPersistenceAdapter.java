package com.fintech.transfer.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.out.TransferRepository;
import com.fintech.transfer.infrastructure.persistence.mapper.TransferPersistenceMapper;
import com.fintech.transfer.infrastructure.persistence.repository.TransferJpaRepository;

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

        @Override
        public List<Transfer> findByLifecycleRange(
                        Instant fromInclusive,
                        Instant toExclusive,
                        int page,
                        int size) {

                return repository.findByLifecycleRange(
                                fromInclusive,
                                toExclusive,
                                PageRequest.of(page, size))
                                .stream()
                                .map(mapper::toDomain)
                                .toList();
        }

}