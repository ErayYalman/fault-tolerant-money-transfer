package com.fintech.transfer.application.saga;

import com.fintech.transfer.domain.model.Transfer;

public record SagaDecision(
        SagaAction action,
        Transfer transfer
) {
}