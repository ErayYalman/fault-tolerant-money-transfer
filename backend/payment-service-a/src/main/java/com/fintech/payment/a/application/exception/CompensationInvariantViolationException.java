package com.fintech.payment.a.application.exception;

public final class CompensationInvariantViolationException
        extends RuntimeException {

    public CompensationInvariantViolationException(String message) {
        super(message);
    }
}