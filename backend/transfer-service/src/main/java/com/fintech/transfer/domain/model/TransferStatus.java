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
    COMPENSATED
}