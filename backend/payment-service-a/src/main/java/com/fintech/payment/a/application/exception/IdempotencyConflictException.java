package com.fintech.payment.a.application.exception;

public final class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("A command identity was reused with different content");
    }
}