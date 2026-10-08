package com.fintech.transfer.domain.port.out;

import com.fintech.transfer.domain.model.SagaState;

import java.util.Optional;
import java.util.UUID;

public interface SagaStateRepository {

    SagaState save(SagaState sagaState);

    Optional<SagaState> findByTransferId(UUID transferId);
}