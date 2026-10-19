package com.fintech.payment.a.infrastructure.messaging.kafka;

public final class OutboxTransportException extends RuntimeException {

    public OutboxTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}