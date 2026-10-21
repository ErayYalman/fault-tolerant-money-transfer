package com.fintech.transfer.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fintech.transfer.domain.model.Transfer;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID transferId);

    Optional<Transfer> findByCorrelationId(UUID correlationId);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    List<Transfer> findByLifecycleRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size);
}