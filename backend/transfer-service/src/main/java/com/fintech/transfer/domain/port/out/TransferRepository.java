package com.fintech.transfer.domain.port.out;

import com.fintech.transfer.domain.model.Transfer;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID transferId);

    Optional<Transfer> findByCorrelationId(UUID correlationId);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}