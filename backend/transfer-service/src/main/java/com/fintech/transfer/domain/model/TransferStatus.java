package com.fintech.transfer.domain.model;

public enum TransferStatus {

    PENDING,
    DEBIT_REQUESTED,
    DEBIT_COMPLETED,
    DEBIT_FAILED,
    CREDIT_REQUESTED,
    CREDIT_FAILED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED;

    public boolean canTransitionTo(TransferStatus target) {
        return switch (this) {
            case PENDING ->
                    target == DEBIT_REQUESTED;

            case DEBIT_REQUESTED ->
                    target == DEBIT_COMPLETED
                            || target == DEBIT_FAILED;

            case DEBIT_COMPLETED ->
                    target == CREDIT_REQUESTED;

            case CREDIT_REQUESTED ->
                    target == COMPLETED
                            || target == CREDIT_FAILED;

            case CREDIT_FAILED ->
                    target == COMPENSATING;

            case COMPENSATING ->
                    target == COMPENSATED;

            case DEBIT_FAILED,
                 COMPLETED,
                 COMPENSATED ->
                    false;
        };
    }
}