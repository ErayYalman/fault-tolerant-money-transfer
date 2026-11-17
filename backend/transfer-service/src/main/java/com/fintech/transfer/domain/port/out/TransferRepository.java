package com.fintech.transfer.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferPage;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID transferId);

    Optional<Transfer> findByCorrelationId(UUID correlationId);

    Optional<Transfer> findByOwnerSubjectAndIdempotencyKey(
            String ownerSubject,
            String idempotencyKey);

    List<Transfer> findByLifecycleRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size);

    Optional<Transfer> findByIdAndOwnerSubject(
            UUID transferId,
            String ownerSubject);

    TransferPage findAllByOwnerSubject(
            String ownerSubject,
            int page,
            int size
    );
}