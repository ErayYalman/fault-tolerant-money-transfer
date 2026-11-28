package com.fintech.payment.a.domain.port.out;

import com.fintech.payment.a.domain.model.InternalTransfer;

import java.util.Optional;

public interface InternalTransferRepository {

    InternalTransfer save(InternalTransfer transfer);

    Optional<InternalTransfer> findByOwnerSubjectAndIdempotencyKey(
            String ownerSubject,
            String idempotencyKey
    );
}
