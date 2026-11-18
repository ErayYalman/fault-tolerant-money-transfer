package com.fintech.transfer.domain.exception;

public class RecipientCurrencyMismatchException extends RuntimeException {

    public RecipientCurrencyMismatchException() {
        super("Recipient account currency does not match transfer currency");
    }
}
