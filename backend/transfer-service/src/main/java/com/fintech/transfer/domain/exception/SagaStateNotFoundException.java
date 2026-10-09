package com.fintech.transfer.domain.exception;

import java.util.UUID;

public class SagaStateNotFoundException extends RuntimeException {

    public SagaStateNotFoundException(UUID transferId) {
        super("Saga state not found for transfer: " + transferId);
    }
}