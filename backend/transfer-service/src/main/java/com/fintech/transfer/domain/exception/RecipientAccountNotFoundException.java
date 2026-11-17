package com.fintech.transfer.domain.exception;

public class RecipientAccountNotFoundException extends RuntimeException {

    public RecipientAccountNotFoundException(String accountNumber) {
        super("Recipient account not found: " + accountNumber);
    }
}
