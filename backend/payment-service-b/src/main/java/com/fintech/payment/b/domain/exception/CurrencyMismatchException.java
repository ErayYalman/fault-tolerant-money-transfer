package com.fintech.payment.b.domain.exception;

public final class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException() {
        super("Transaction currency does not match account currency");
    }
}