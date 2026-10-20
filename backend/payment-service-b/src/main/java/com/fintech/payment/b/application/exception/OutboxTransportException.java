package com.fintech.payment.b.application.exception;

public final class OutboxTransportException extends RuntimeException {

    public OutboxTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}