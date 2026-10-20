package com.fintech.payment.b.application.exception;

public final class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}