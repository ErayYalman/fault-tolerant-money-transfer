package com.fintech.transfer.domain.exception;

public class RecipientAccountNotActiveException extends RuntimeException {

    public RecipientAccountNotActiveException() {
        super("Recipient account is not active");
    }
}
