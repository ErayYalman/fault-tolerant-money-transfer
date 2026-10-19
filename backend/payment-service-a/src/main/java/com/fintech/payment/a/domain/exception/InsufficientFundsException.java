package com.fintech.payment.a.domain.exception;

public final class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Insufficient funds");
    }
}