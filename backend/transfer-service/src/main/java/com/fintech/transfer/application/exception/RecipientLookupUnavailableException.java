package com.fintech.transfer.application.exception;

public class RecipientLookupUnavailableException extends RuntimeException {

    public RecipientLookupUnavailableException() {
        super("Payment Service B returned an empty response");
    }

    public RecipientLookupUnavailableException(Throwable cause) {
        super("Payment Service B recipient lookup failed", cause);
    }
}
